(ns pid-control.core-test
  (:require [clojure.test :refer :all]
            [pid-control.core :refer :all]
            [clojure.java.io :as io]))

(deftest parse-telemetry-test
  (testing "Can parse a telemetry message"
    (let [parsed (parse-telemetry (slurp (io/resource "telemetry-message.txt")))]
      (is (= 0.7598 (:cte parsed)))
      (is (= 0.2 (:steering-angle parsed)))
      (is (= 0.5 (:throttle parsed)))
      (is (= 10.3 (:speed parsed)))))
  (testing "Can skip empty messages"
    (is (= nil (parse-telemetry nil)))
    (is (= nil (parse-telemetry "2")))))
