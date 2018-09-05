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
        s->y (interpolate (map vector s y) :cubic)]
    {:waypoints waypoints
     ; List of s values for the waypoints
     :s s
     ; Direct converters from s to (x,y) when d=0
     :s->x s->x
     :s->y s->y}))

(defn s->dxy-ds [track s]
  "Partial derivatives dx/ds and dy/ds at s"
  (let [s1 (- s 0.01)
        s2 (+ s 0.01)
        s->x (:s->x track)
        s->y (:s->y track)
        dx (- (s->x s2) (s->x s1))
        dy (- (s->y s2) (s->y s1))
        ds (Math/sqrt (+ (* dx dx) (* dy dy)))]
    [(/ dx ds) (/ dy ds)]))

(defn s->dxy-dd [track s]
  "Partial derivatives dx/dd and dy/dd at s"
  (let [[dx-ds dy-ds] (s->dxy-ds track s)]
    [dy-ds (- dx-ds)]))

(defn s->xy [track s]
  "Convert s to (x,y) assuming d=0"
  [((:s->x track) s) ((:s->y track) s)])

(defn sd->xy [track s d]
  (let [[path-x path-y] (s->xy track s)
        [dx-dd dy-dd] (s->dxy-dd track s)
        x (+ path-x (* d dx-dd))
        y (+ path-y (* d dy-dd))]
    [x y]))

(defn guardrail-newton
  "Version of Newton's method with guardrails to ensure convergence"
  ([f x0 tolerance]
   (guardrail-newton f x0 tolerance nil nil))
  ([f x0 tolerance min-x max-x]
   (let [f0 (f x0)
         df-dx (/ (- (f (+ x0 tolerance))
                     (f (- x0 tolerance)))
                  (+ tolerance tolerance))
         dx (/ (- f0) df-dx)
         new-min-x (if (< dx 0) min-x x0)
         new-max-x (if (< dx 0) x0 max-x)
         max-dx (if (and min-x max-x)
                  (* 0.5 (- max-x min-x))
                  (Math/abs dx))
         restrained-dx (cond
                         (> dx max-dx) max-dx
                         (< dx (- max-dx)) (- max-dx)
                         :else dx)
         next-guess (+ x0 restrained-dx)
         restrained-next-guess (cond
                                 (and min-x (< next-guess min-x)) min-x
                                 (and max-x (> next-guess max-x)) max-x
                                 :else next-guess)]
     ;(println (str "f:" f0 " df/dx:" df-dx " dx:" dx " ... "
     ;           x0 " -> " restrained-next-guess " [ " min-x " " max-x " ]"]
     (if (< (Math/abs restrained-dx) tolerance)
       next-guess
       (recur f next-guess tolerance new-min-x new-max-x)))))

(defn xy->sd [track x y]
  (let [estimated-delta-s (fn [s0]
                            (let [[x0 y0] (s->xy track s0)
                                  [dx-ds dy-ds] (s->dxy-ds track s0)]
                              (+ (* dx-ds (- x x0))
                                 (* dy-ds (- y y0)))))
        closest-waypoint-i (apply min-key #(distance [x y] (get (:waypoints track) %))
                             (range (count (:waypoints track))))
        closest-waypoint-s (get (:s track) closest-waypoint-i)
        next-waypoint-s (if (< closest-waypoint-i (count (:waypoints track)))
                          (get (:s track) (inc closest-waypoint-i))
                          nil)
        prev-waypoint-s (if (= closest-waypoint-i 0)
                          nil
                          (get (:s track) (dec closest-waypoint-i)))
        s (guardrail-newton estimated-delta-s closest-waypoint-s
            0.001 prev-waypoint-s next-waypoint-s)
        [x0 y0] (s->xy track s)
        [dd-dx dd-dy] (s->dxy-dd track s)
        d (+ (* dd-dx (- x x0)) (* dd-dy (- y y0)))]
    ;(println (str "x0:" x0 " y0:" y0 " d:" d))
    [s d]))

(defn sdv->xyv [track s d vs vd]
  (let [[x0 y0] (sd->xy track s d)
        tiny 0.01
        [x1 y1] (sd->xy track (+ s (* tiny vs)) (+ d (* tiny vd)))
        vx (/ (- x1 x0) tiny)
        vy (/ (- y1 y0) tiny)]
    [x0 y0 vx vy]))

(defn xyv->sdv [track x y vx vy]
  (let [[s0 d0] (xy->sd track x y)
        tiny 0.01
        [s1 d1] (xy->sd track (+ x (* tiny vx)) (+ y (* tiny vy)))
        vs (/ (- s1 s0) tiny)
        vd (/ (- d1 d0) tiny)]
    [s0 d0 vs vd]))

