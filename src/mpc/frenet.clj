(ns mpc.frenet
  (:require [incanter.interpolation :refer [interpolate]]))

(defn distance [[x1 y1] [x2 y2]]
  (Math/sqrt (+ (Math/pow (- x1 x2) 2)
                (Math/pow (- y1 y2) 2))))

(defn track
  "Construct a coordinate system around a track, based
   on a set of (x,y) waypoints along that track.
   Coordinates are (s,d) where s is distance along the
   track and d is distance to the right of the track.
   Internally, d by itself is part of (s,d) coordinates
   and dM-dN is a partial derivative of M with respect
   to N. Surprisingly, in the case of frenet coordinates,
   the following convenient partial derivative equalities
   hold:
     dx/ds = ds/dx = -dy/dd = -dd/dy
     dy/ds = ds/dy = dx/dd = dd/dx
     (dx/ds)^2 + (dy/ds)^2 = 1
   (Such equalities are possible because partial derivatives
    are not fractions, despite what the notation suggests.)
   These equalities will be used heavily in this namespace.
   For example, after representing dx/ds and dy/ds with
   splines, there's no need to create separate splines for
   the other quantities that are easily derived from those
   two."
  [waypoints]
  (let [distances (mapv distance (rest waypoints) (butlast waypoints))
        s (vec (reductions + (concat [0] distances)))
        x (map #(get % 0) waypoints)
        y (map #(get % 1) waypoints)
        s->x (interpolate (map vector s x) :cubic)
        s->y (interpolate (map vector s y) :cubic)
        dx-ds (map (fn [s0]
                     (/ (- (s->x (+ s0 0.01))
                           (s->x (- s0 0.01)))
                        0.02))
                s)
        dy-ds (map (fn [s0]
                     (/ (- (s->y (+ s0 0.01))
                           (s->y (- s0 0.01)))
                        0.02))
                s)
        s->dx-ds (interpolate (map vector s dx-ds) :cubic)
        s->dy-ds (interpolate (map vector s dy-ds) :cubic)]
    {:waypoints waypoints
     ; List of s values for the waypoints
     :s s
     ; Direct converters from s to (x,y) when d=0
     :s->x s->x
     :s->y s->y
     ; For given s, what are the partial derivatives dx/ds and dy/ds?
     ; Due to symmetry, many other partial derivates are easily derivable from these.
     :s->dx-ds s->dx-ds
     :s->dy-ds s->dy-ds}))

(defn sd->xy [track s d]
  (let [path-x ((:s->x track) s)
        path-y ((:s->y track) s)
        dx-ds ((:s->dx-ds track) s)
        dy-ds ((:s->dy-ds track) s)
        dx-dd dy-ds
        dy-dd (- dx-ds)
        x (+ path-x (* d dx-dd))
        y (+ path-y (* d dy-dd))]
    [x y]))
  
(defn xy->sd [track x y]
  (let [refine-s (fn [s0]
                   (let [[x0 y0] (sd->xy track s0 0)
                         dx-ds ((:s->dx-ds track) s0)
                         dy-ds ((:s->dy-ds track) s0)]
                     (+ s0 (* dx-ds (- x x0)) (* dy-ds (- y y0)))))
        closest-waypoint-i (apply min-key #(distance [x y] (get (:waypoints track) %))
                             (range (count (:waypoints track))))
        closest-waypoint-s (get (:s track) closest-waypoint-i)
        s (loop [s0 closest-waypoint-s
                 i 0]
            (let [new-s (refine-s s0)]
              ;(println (str "s: " s0 " -> " new-s))
              (cond
                (> 0.00001 (Math/abs (- new-s s0))) new-s
                (< i 2) (recur new-s (inc i))
                (< i 4) (recur (+ s0 (* 0.5 (- new-s s0))) (inc i))
                (< i 10) (recur (+ s0 (* 0.1 (- new-s s0))) (inc i))
                :else (+ s0 (* 0.05 (- new-s s0))))))
        [x0 y0] (sd->xy track s 0)
        dx-ds ((:s->dx-ds track) s)
        dy-ds ((:s->dy-ds track) s)
        dd-dx dy-ds
        dd-dy (- dx-ds)
        d (+ (* dd-dx (- x x0)) (* dd-dx (- y y0)))
        distance (Math/sqrt (+ (Math/pow (- y y0) 2) (Math/pow (- x x0) 2)))]
    ;(println (str "x0:" x0 " y0:" y0 " d:" d " distance:" distance))
    [s (if (> d 0) distance (- distance))]))

