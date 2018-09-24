(defproject mpc "0.1.0-SNAPSHOT"
  :description "Clojure version of MPC project from Udacity's self-driving car engineer nanodegree"
  :url "https://github.com/ericlavigne/CarND-MPC-Clojure"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/data.json "0.2.6"]
                 [figurer "0.1.0"]
                 [frenet "0.1.0"]
                 [http-kit "2.3.0"]
                 [incanter/incanter-core "1.9.3"]
                 [jarohen/chord "0.8.1" :exclude http-kit]
                 [same/ish "0.1.1"]]
  :main ^:skip-aot mpc.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
