# CarND Model Predictive Control
[![Udacity - Self-Driving Car NanoDegree](https://s3.amazonaws.com/udacity-sdc/github/shield-carnd.svg)](http://www.udacity.com/drive)

Self-Driving Car Engineer Nanodegree Program

---

Clojure version of Udacity's MPC project from term 2 of the self-driving
car engineer nanodegree. This repository is intended to serve as starter code for
other students who wish to complete the project in Clojure.

## Why Clojure?

The most common choices for self-driving car development are C++ and Python.
[Clojure](https://clojure.org/)
supports a faster development style than either of these languages (especially C++).
Compared to C++, Clojure has a much simpler and more flexible syntax, clear
error handling, and sophisticated dependency management. Compared to Python, Clojure is
much faster (close to C++) and has excellent concurrency support.

Here's a [tutorial](https://clojure.org/guides/learn/syntax#_clojure_basics)
and [interactive prompt](http://clojurescript.net/) to help you get started.

## Installation

You will neeed to install
[Leiningen](https://leiningen.org/),
a Clojure build tool. This is a fairly easy
installation process. Just follow the instructions on the
[Leiningen](https://leiningen.org/) website.

I also recommend [NightCode](https://sekao.net/nightcode/) as your first Clojure text
editor because it is very easy to install and use. Later, you can explore more advanced
options like [Cursive (IntelliJ)](https://cursive-ide.com/),
[CIDER (Emacs)](https://github.com/clojure-emacs/cider),
or [Vim](https://github.com/tpope/vim-fireplace).

## Usage

You'll find many TODO comments in src/mpc/core.clj indicating parts of
the code that you will need to complete. The code already runs as-is,
but the car will drive poorly until you make improvements.

You can run the code with the following command. You should also run
[Udacity's term 2 simulator](https://github.com/udacity/self-driving-car-sim/releases)
at the same time and select the "MPC Control" project.

    $ lein run

The idea of model predictive control is to describe the problem and desired outcome,
then let an optimization library find a good solution to that problem. In this case,
we use the [figurer](https://github.com/ericlavigne/figurer) library to perform the
optimization. You will need to inform figurer about what kind of outcome you want
(value function), the available actions and their likelihood of each action (policy
function), and the mechanics of this problem (prediction function). Start with
simple versions of each of these functions, then enhance these functions to improve
your car's steering. For added challenge, see how fast you can go without leaving
the track. (Over 100 MPH is possible!)

## Solution

You should complete this project on your own first. After you've completed the project,
you can review
[my solution](https://github.com/ericlavigne/CarND-MPC-Clojure/compare/solution)
to see how it compares.

## License

Copyright © 2018 Eric Lavigne

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
