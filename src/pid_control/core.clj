(ns pid-control.core
  (:require [chord.http-kit :refer [with-channel wrap-websocket-handler]]
            [org.httpkit.server :refer [run-server]]
            [clojure.core.async :refer [<! >! put! close! go]]
            [clojure.core.async :as a]
            [clojure.data.json :as json]
            [clojure.string :refer [index-of last-index-of]])
  (:gen-class))

(defn parse-telemetry [msg]
  (if (and msg
           (> (.length msg) 2)
           (= (subs msg 0 2) "42"))
    (let [json-start (index-of msg "[")
          json-end (last-index-of msg "]")
          json-str (subs msg json-start (inc json-end))
          json-msg (json/read-str json-str)]
      (if (= (get json-msg 0) "telemetry")
        (let [data (get json-msg 1)]
          {:cte (Double/parseDouble (get data "cte"))
           :steering-angle (Double/parseDouble (get data "steering_angle"))
           :throttle (Double/parseDouble (get data "throttle"))
           :speed (Double/parseDouble (get data "speed"))
           ;:image (get data "image")
           })
        json-msg))
    nil))

(defn handler [{:keys [ws-channel] :as req}]
  (go
    (let [{:keys [message]} (<! ws-channel)
          telemetry (parse-telemetry message)]
      (if message
        (println (str (or telemetry message))))
      (if telemetry
        (let [response "42[\"steer\",{\"steering_angle\":0.0,\"throttle\":0.3}]"]
          (>! ws-channel response)
          (println response)))
      ;(>! ws-channel "42[\"manual\",{}]")
      ;(close! ws-channel)
      )))

(defn -main
  "Run websocket server to communicate with Udacity PID simulator."
  [& args]
  (println "Starting server")
  (run-server (-> #'handler
                  (wrap-websocket-handler
                    {:read-ch (a/chan (a/dropping-buffer 10))
                     :format :str}))
              {:port 4567})
  )
