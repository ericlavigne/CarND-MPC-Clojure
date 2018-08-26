(ns mpc.core
  (:require [chord.http-kit :refer [with-channel wrap-websocket-handler]]
            [org.httpkit.server :refer [run-server]]
            [clojure.core.async :refer [<! >! go-loop chan dropping-buffer]]
            [clojure.data.json :as json]
            [clojure.string :refer [index-of last-index-of]])
  (:gen-class))

(def steering-pid-parameters
     {:proportional-factor 0.12
      :derivative-factor 1.8
      :integral-factor 0.005})

(def speed 20)

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
  [{:keys [steering-angle throttle] :as actuation}]
  (str "42[\"steer\",{\"steering_angle\":"
       steering-angle
       ",\"throttle\":"
       throttle
       "}]"))

(defn parse-message
  "Parse message from Udacity's SDC term 2 simulator for the PID project."
  [msg]
  (println msg)
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

(def pid (atom nil))
(def previous-milliseconds (atom nil))
(def actuation-period-milliseconds 50)

(defn handler
  "Called in response to websocket connection. Handles sending and receiving messages."
  [{:keys [ws-channel] :as req}]
  (go-loop []
    (let [{:keys [message]} (<! ws-channel)
          parsed (parse-message message)
          current-milliseconds (.getTime (java.util.Date.))]
      (if message
        (println (str (or parsed message))))
      (when parsed
        (when (= :telemetry (:type parsed))
          (when (not @pid)
            (reset! pid (initial-pid (:cte parsed)))
            (reset! previous-milliseconds current-milliseconds))
          (let [milliseconds-elapsed (- current-milliseconds @previous-milliseconds)
                milliseconds-until-actuation (- actuation-period-milliseconds milliseconds-elapsed)]
            (when (<= milliseconds-until-actuation 0)
              (swap! pid update-pid (:cte parsed) (* milliseconds-elapsed 0.001 (max 1.0 (:speed parsed))))
              (reset! previous-milliseconds current-milliseconds))
            (when (> milliseconds-until-actuation 0)
              (Thread/sleep milliseconds-until-actuation))
            (let [response (format-actuation {:steering-angle (pid-actuation @pid steering-pid-parameters)
                                              :throttle (if (< (:speed parsed) speed) 1.0 0.0)})]
              (>! ws-channel response)
              (when (= current-milliseconds @previous-milliseconds)
                (println response)))))
        (when (= :manual (:type parsed))
          (reset! pid nil)
          (Thread/sleep actuation-period-milliseconds)
          (>! ws-channel "42[\"manual\",{}]")))
      (recur))))

(defn -main
  "Run websocket server to communicate with Udacity PID simulator."
  [& args]
  (println "Starting server")
  (run-server (-> #'handler
                  (wrap-websocket-handler
                    {:read-ch (chan (dropping-buffer 10))
                     :format :str}))
              {:port 4567}))
