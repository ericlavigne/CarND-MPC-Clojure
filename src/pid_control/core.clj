(ns pid-control.core
  (:require [chord.http-kit :refer [with-channel wrap-websocket-handler]]
            [org.httpkit.server :refer [run-server]]
            [clojure.core.async :refer [<! >! go-loop chan dropping-buffer]]
            [clojure.data.json :as json]
            [clojure.string :refer [index-of last-index-of]])
  (:gen-class))

(defn format-actuation
  "Format actuation (:steering-angle and :throttle) for transmission to simulator."
  [actuation]
  (str "42[\"steer\",{\"steering_angle\":"
       (:steering-angle actuation)
       ",\"throttle\":"
       (:throttle actuation)
       "}]"))

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
             :cte (Double/parseDouble (get data "cte"))
             :steering-angle (Double/parseDouble (get data "steering_angle"))
             :throttle (Double/parseDouble (get data "throttle"))
             :speed (Double/parseDouble (get data "speed"))
             ;:image (get data "image")
            }
            {:type :manual}))
        json-msg))
    nil))

(defn handler
  "Called in response to websocket connection. Handles sending and receiving messages."
  [{:keys [ws-channel] :as req}]
  (go-loop []
    (let [{:keys [message]} (<! ws-channel)
          parsed (parse-message message)]
      (if message
        (println (str (or parsed message))))
      (when parsed
        (let [response (case (:type parsed)
                         :telemetry (format-actuation {:steering-angle -0.05 :throttle 0.3})
                         :manual    "42[\"manual\",{}]"
                         (throw (Exception. (str "Unrecognized message type " (:type parsed)))))]
            (>! ws-channel response)
            (println response)))
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
