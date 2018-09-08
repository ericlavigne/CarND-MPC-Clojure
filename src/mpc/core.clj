(ns mpc.core
  (:require [chord.http-kit :refer [with-channel wrap-websocket-handler]]
            [org.httpkit.server :refer [run-server]]
            [clojure.core.async :refer [<! >! go-loop chan dropping-buffer]]
            [clojure.data.json :as json]
            [clojure.string :refer [index-of last-index-of]]
            [mpc.frenet :as frenet])
  (:gen-class))

(def steering-pid-parameters
     {:proportional-factor 0.12
      :derivative-factor 1.8
      :integral-factor 0.005})

(def speed 50)

(defn initial-pid
  "Set PID errors using only the first measurement."
  [measured-error]
  {:proportional-error measured-error
   :derivative-error 0.0
   :integral-error 0.0})

(defn pid-actuation
  "Use PID to select actuation (such as steering angle)."
  [{:keys [proportional-error derivative-error integral-error] :as pid}
   {:keys [proportional-factor derivative-factor integral-factor] :as pid-parameters}]
  (- (+ (* proportional-factor proportional-error)
        (* derivative-factor derivative-error)
        (* integral-factor integral-error))))

(defn update-pid
  "Use new error measurement to update PID errors."
  [{:keys [proportional-error derivative-error integral-error] :as pid}
   measured-error time-passed]
  {:proportional-error measured-error
   :derivative-error (/ (- measured-error proportional-error) time-passed)
   :integral-error (+ integral-error (* measured-error time-passed))})

(defn format-actuation
  "Format actuation (:steering-angle and :throttle) for transmission to simulator."
  [{:keys [steering-angle throttle waypoints plan] :as actuation}]
  (let [[way-x way-y] (apply mapv vector waypoints)
        [plan-x plan-y] (apply mapv vector plan)]
    (str "42"
      (json/write-str
        ["steer"
         {"steering_angle" steering-angle
          "throttle" throttle
          "next_x" way-x
          "next_y" way-y
          "mpc_x" plan-x
          "mpc_y" plan-y}]))))

(defn parse-message
  "Parse message from Udacity's SDC term 2 simulator for the PID project."
  [msg]
  (if (and msg
           (> (.length msg) 2)
           (= (subs msg 0 2) "42"))
    (let [json-start (index-of msg "[")
          json-end (last-index-of msg "]")
          json-str (subs msg json-start (inc json-end))
          json-msg (json/read-str json-str)]
      (if (= (get json-msg 0) "telemetry")
        (let [data (get json-msg 1)]
          (if data
            {:type :telemetry
             :ptsx (get data "ptsx")
             :ptsy (get data "ptsy")
             :x (get data "x")
             :y (get data "y")
             :speed (get data "speed")
             :psi (get data "psi")
             :psi-unity (get data "psi_unity")
             :steering-angle (get data "steering_angle")
             :throttle (get data "throttle")}
            {:type :manual}))
        json-msg))
    nil))

(defn convert-point-to-vehicle-frame
  "Convert a point from absolute coordinates to vehicle reference frame"
  [absxy carxy carpsi]
  (let [distance (Math/sqrt (+ (Math/pow (- (absxy 0) (carxy 0)) 2)
                               (Math/pow (- (absxy 1) (carxy 1)) 2)))
        direction-abs (Math/atan2 (- (absxy 1) (carxy 1)) (- (absxy 0) (carxy 0)))
        direction-rel (- direction-abs carpsi)
        relx (* distance (Math/cos direction-rel))
        rely (* distance (Math/sin direction-rel))]
    [relx rely]))

(defn convert-points-to-vehicle-frame
  "Convert list of x and list of y from absolute coordinates to vehicle reference frame"
  [absx-list absy-list carxy carpsi]
  (mapv #(convert-point-to-vehicle-frame [%1 %2] carxy carpsi) absx-list absy-list))

(defn policy
  "Given current state, determine next actuation."
  [global state]
  (let [[x y psi v vx vy s d vs vd] state
        steering (min 1.0
                   (max -1.0
                     (pid-actuation
                       {:proportional-error d
                        :derivative-error (/ vd
                                             (Math/sqrt
                                               (+ (* vd vd)
                                                  (* vs vs)
                                                  0.1)))
                        :integral-error 0.0}
                       steering-pid-parameters)))
        throttle (if (< vs speed) 1.0 0.0)]
    [steering throttle]))

(defn predict
  "Given current state and actuation, determine how
   the state will change."
  [global state actuation dt]
  (let [[x0 y0 psi0 v0 vx0 vy0 s0 d0 vs0 vd0] state
        [steering throttle] actuation
        coord (:frenet global)
        Lf 2.67
        steer_radians (* 25 steering (/ Math/PI 180))
        ; Physics
        x (+ x0 (* vx0 dt))
        y (+ y0 (* vy0 dt))
        psi (- psi0 (* v0 dt (/ steer_radians Lf)))
        v (+ v0 (* throttle dt))
        ; Derived parts of state
        vx (* v (Math/cos psi))
        vy (* v (Math/sin psi))
        [s d vs vd] (frenet/xyv->sdv coord x y vx vy)]
    [x y psi v vx vy s d vs vd]))

(defn value
  "Measure of how 'good' a state is. A plan will
   be chosen that maximizes the average result of
   this function across each state in the plan."
  [state]
  (let [[x y psi v vx vy s d vs vd] state
        progress s
        distance-from-center (Math/abs d)
        sideways-speed (Math/abs vd)
        on-road (< distance-from-center 3.0)]
    (+ progress
       (- distance-from-center)
       (- sideways-speed)
       (if on-road 0.0 -100.0))))

(defn controller
  "Given telemetry (information about vehicle's situation)
   decide actuation (steering angle and throttle)."
  [telemetry]
  (let [rel-waypoints (convert-points-to-vehicle-frame
                                (:ptsx telemetry) (:ptsy telemetry)
                                [(:x telemetry) (:y telemetry)]
                                (:psi telemetry))
        [x y] [0 0]
        [vx vy] [(:speed telemetry) 0]
        coord (frenet/track rel-waypoints)
        [s d vs vd] (frenet/xyv->sdv coord x y vx vy)
        global {:frenet coord}
        state [x y 0.0 vx vx vy s d vs vd]
        [steering throttle] (policy global state)
        plan (take 10 (iterate
                        (fn [state]
                          (let [[steering throttle] (policy global state)]
                            (predict global state [steering throttle] 0.1)))
                        state))
        plan-value (reduce + (map value plan))
        plan-xy (mapv #(vec (take 2 %)) plan)]
    (println (str "Value: " (Math/round plan-value)))
    {:steering-angle steering
     :throttle throttle
     :waypoints rel-waypoints
     :plan plan-xy}))

(def actuation-period-milliseconds 50)

(defn handler
  "Called in response to websocket connection. Handles sending and receiving messages."
  [{:keys [ws-channel] :as req}]
  (go-loop []
    (let [{:keys [message]} (<! ws-channel)
          parsed (parse-message message)]
      (when parsed
        (when (= :telemetry (:type parsed))
          (Thread/sleep actuation-period-milliseconds)
          (let [response (format-actuation (controller parsed))]
            (>! ws-channel response))))
      (when (= :manual (:type parsed))
        (Thread/sleep actuation-period-milliseconds)
        (>! ws-channel "42[\"manual\",{}]")))
    (recur)))

(defn -main
  "Run websocket server to communicate with Udacity PID simulator."
  [& args]
  (println "Starting server")
  (run-server (-> #'handler
                  (wrap-websocket-handler
                    {:read-ch (chan (dropping-buffer 10))
                     :format :str}))
              {:port 4567}))
