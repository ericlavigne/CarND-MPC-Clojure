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
        [s d vs vd] (frenet/xyv->sdv coord x y vx vy)]
    {:steering-angle (min 1.0
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
     :throttle (if (< (:speed telemetry) speed) 1.0 0.0)
     :waypoints rel-waypoints
     :plan rel-waypoints}))

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
