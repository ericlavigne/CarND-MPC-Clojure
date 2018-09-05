(ns mpc.frenet-test
  (:require [clojure.test :refer :all]
            [mpc.frenet :refer :all]
            same))

(defn is-close [x1 x2]
  (if (< (Math/abs (- x2 x1)) 0.001)
    (is true)
    (is (= x1 x2))))

(defn check-frenet-reversible [track x y s d]
  (let [[s2 d2] (xy->sd track x y)
        [x2 y2] (sd->xy track s2 d2)]
    (is-close s s2)
    (is-close d d2)
    (is-close x x2)
    (is-close y y2)))


(def bumpy-track (track [[1 2] [2 1] [3 2] [4 1] [5 2]]))

(deftest bumpy-close-test
  (testing "Bumpy track, middle of track"
    (check-frenet-reversible bumpy-track 2.0 1.0 1.414 0.0))
  (testing "Bumpy track, 0.1 m left of center"
    (check-frenet-reversible bumpy-track 2.0 1.1 1.48 -0.08875))
  (testing "Bumpy track, 0.1 m right of center"
    (check-frenet-reversible bumpy-track 2.0 0.9 1.3748 0.0939)))

(def gentle-track (track [[0 0] [1 2] [2 3.5] [3 4.5] [4 4.5] [5 4] [6 3] [7 2.7] [8 3.3] [9 4.6]]))

(deftest gentle-test
  (testing "Gentle track, s=0 d=0"
    (check-frenet-reversible gentle-track 0.0 0.0 0.0 0.0))
  (testing "Gentle track, x=1 y=2 s=2.5 d=0"
    (check-frenet-reversible gentle-track 1.0 2.0 2.236 0.0))
  (testing "Gentle track, x=2 y=1 s=2.5 d=1.5"
    (check-frenet-reversible gentle-track 2.0 1.0 1.831 1.363))
  (testing "Gentle track, x=0 y=3 s=2.5 d=-1.5"
    (check-frenet-reversible gentle-track 0.0 3.0 2.555 -1.375))
  (testing "Gentle track, x=3 y=3.5 s=5 d=1"
    (check-frenet-reversible gentle-track 3.0 3.5 4.651 0.807))
  (testing "Gentle track, x=2 y=5 s=6 d=-1"
    (check-frenet-reversible gentle-track 2.0 5.0 4.999 -0.961)))

