(ns pid-control.core-test
  (:require [clojure.test :refer :all]
            [pid-control.core :refer :all]
            [clojure.java.io :as io]
            same))

(deftest parse-message-test
  (testing "Can parse a telemetry message"
    (let [parsed (parse-message (slurp (io/resource "telemetry-message.txt")))]
      (is (= 0.7598 (:cte parsed)))
      (is (= 0.2 (:steering-angle parsed)))
      (is (= 0.5 (:throttle parsed)))
      (is (= 10.3 (:speed parsed)))))
  (testing "Can skip empty messages"
    (is (= nil (parse-message nil)))
    (is (= nil (parse-message "2"))))
  (testing "Can parse manual message"
    (is (= {:type :manual}
           (parse-message "42[\"telemetry\",null]")))))

(deftest initial-pid-test
  (testing "Initial PID has 0 error if there is 0 measurement error"
    (is (= (initial-pid 0.0)
           {:proportional-error 0.0
            :derivative-error 0.0
            :integral-error 0.0})))
  (testing "Initial PID proportional error matches measurement error"
    (is (= (initial-pid 1.0)
           {:proportional-error 1.0
            :derivative-error 0.0
            :integral-error 0.0}))
    (is (= (initial-pid -1.0)
           {:proportional-error -1.0
            :derivative-error 0.0
            :integral-error 0.0}))))

(deftest pid-actuation-test
  (testing "Proportional actuation steers right (positive) when the car is too far to the left (negative)"
    (is (same/ish? (pid-actuation {:proportional-error  -2.0 :derivative-error   0.0 :integral-error   0.0}
                                  {:proportional-factor  0.1 :derivative-factor  0.0 :integral-factor  0.0})
                   0.2)))
  (testing "Proportional actuation steers left (negative) when the car is too far to the right (positive)"
    (is (same/ish? (pid-actuation {:proportional-error   2.0 :derivative-error   0.0 :integral-error   0.0}
                                  {:proportional-factor  0.1 :derivative-factor  0.0 :integral-factor  0.0})
                   -0.2)))
  (testing "Derivative actuation prevents oscillation by slowing down changes in the error"
    (is (same/ish? (pid-actuation {:proportional-error   0.0 :derivative-error   0.5 :integral-error   0.0}
                                  {:proportional-factor  0.0 :derivative-factor  0.2 :integral-factor  0.0})
                   -0.1)))
  (testing "Integral actuation slowly pulls the car back to center if it spends too much time on one side of the road"
    (is (same/ish? (pid-actuation {:proportional-error   0.0 :derivative-error   0.0 :integral-error  -1.5}
                                  {:proportional-factor  0.0 :derivative-factor  0.0 :integral-factor  0.01})
                   0.015)))
  (testing "All three actuation types work together to steer the car smoothly"
    (is (same/ish? (pid-actuation {:proportional-error   2.0 :derivative-error   0.5 :integral-error  -1.5}
                                  {:proportional-factor  0.1 :derivative-factor  0.2 :integral-factor  0.01})
                   -0.285))))

(deftest update-pid-test
  (testing "update-pid applies a new error measurement to calculate the new values of all PID error components"
    (is (same/ish? (update-pid {:proportional-error 0.11 :derivative-error -0.5 :integral-error 7.0}
                               0.105 0.03)
                   {:proportional-error 0.105, :derivative-error -0.16666666666666682, :integral-error 7.00315}))))

(deftest pid-parameters-test
  (testing "Need to experiment with the pid parameters to find values that keep the car on the road"
    (is (or (> (:proportional-factor steering-pid-parameters) 0.001)
            (> (:derivative-factor steering-pid-parameters) 0.001)
            (> (:integral-factor steering-pid-parameters) 0.001)))))
