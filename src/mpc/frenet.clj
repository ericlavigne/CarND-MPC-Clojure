(ns mpc.frenet
  (:require [incanter.interpolation :refer [interpolate]]))

(defn track [waypoints]
  (let [s (range (count waypoints))
        x (map #(get % 0) waypoints)
        y (map #(get % 1) waypoints)
        s-x (interpolate (map vector s x) :cubic)
        s-y (interpolate (map vector s y) :cubic)
        dx (map #(- (s-x (+ % 0.01)) (s-x (- % 0.01))) s)
        dy (map #(- (s-y (+ % 0.01)) (s-y (- % 0.01))) s)
        dmag (map #(Math/sqrt (+ (Math/pow %1 2) (Math/pow %2 2))) dx dy)
        dx (map #(/ %1 %2) dx dmag)
        dy (map #(/ %1 %2) dy dmag)]
    {:waypoints waypoints
     :s-x s-x
     :s-y s-y
     :s-dx (interpolate (map vector s dx) :cubic)
     :s-dy (interpolate (map vector s dy) :cubic)}))

(defn sd-to-xy [track s d]
  (let [path-x ((:s-x track) s)
        path-y ((:s-y track) s)
        dx ((:s-dx track) s)
        dy ((:s-dy track) s)
        ; d is 90 degrees clockwise from the dx,dy tangent line in (dy,-dx) direction
        x (+ path-x (* d dy))
        y (+ path-y (* d (- dx)))]
    [x y]))
  

