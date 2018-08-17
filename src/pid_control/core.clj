(ns pid-control.core
  (:require [chord.http-kit :refer [with-channel wrap-websocket-handler]]
            [org.httpkit.server :refer [run-server]]
            [clojure.core.async :refer [<! >! put! close! go]]
            [clojure.core.async :as a])
  (:gen-class))

(defn parse-telemetry [msg]
  {}
)

(defn handler [{:keys [ws-channel] :as req}]
  (go
    (let [{:keys [message]} (<! ws-channel)]
      (println "Message received:" message)
      (>! ws-channel "Hello client from server!")
      (close! ws-channel))))

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
