(ns figurer.core)

(defn create
  "Defines the context for all simulation and optimization.
  
     policy: function from state to actuation
     value: function from state to number
     predict: function from state and actuation to state
     initial-state: state is a vector of doubles
     depth: integer indicating how many timesteps to consider
  
   TODO: Initialize nodes."
  [{:keys [policy value predict initial-state depth] :as options}]
  options)

(defn figure
  "Perform optimization, returning a more optimized context.
   TODO: Actually optimize... currently just returns same
   context."
  [context {:keys [max-iterations max-seconds max-nodes] :as options}]
  context)

(defn sample-policy
  "Samples from optimized policy for a given state
   (defaulting to initial state). For maximizing
   policies, the result should be the action that
   is expected to maximize the value. For random
   policies, the initial random distribution will
   be sampled.
   TODO: Use simulation results to refine rather
   than just sampling from the initial policy."
  ([context] (sample-policy context (:initial-state context)))
  ([context state] ((:policy context) state)))

(defn sample-plan
  "Returns a list of states and actuations starting
   from a given state (defaulting to initial state).
   Result states is longer than actuations because
   initial state is included.
   TODO: Use simulation results to refine rather
   than just sampling from the initial policy."
  ([context] (sample-plan context (:initial-state context)))
  ([context initial-state]
   (loop [last-state initial-state
          previous-states [initial-state]
          previous-actuations []
          remaining (:depth context)]
     (if (< remaining 1)
       {:states previous-states :actuations previous-actuations}
       (let [actuation (sample-policy context last-state)
             state ((:predict context) last-state actuation)]
         (recur state (conj previous-states state)
           (conj previous-actuations actuation)
           (dec remaining)))))))

(defn expected-value
  "Estimates the average value that would be
   found by sampling many plans from this
   initial state."
  ([context] (expected-value context (:initial-state context)))
  ([context state]
   (let [plan (sample-plan context state)
         states (:states plan)
         total-value (reduce + (map (:value context) states))
         num-states (count states)
         average-value (/ total-value num-states)]
     average-value)))

