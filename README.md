# CarND PID Control
[![Udacity - Self-Driving Car NanoDegree](https://s3.amazonaws.com/udacity-sdc/github/shield-carnd.svg)](http://www.udacity.com/drive)

Self-Driving Car Engineer Nanodegree Program

---

Clojure version of Udacity's PID controller project from term 2 of the self-driving
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

You'll find many TODO comments in src/pid_control/core.clj indicating parts of
the code that you will need to complete. You are encouraged to run the automated
tests frequently to confirm that your changes work correctly.

    $ lein test

When the code is complete, you can run it with the following command. You should also run
[Udacity's term 2 simulator](https://github.com/udacity/self-driving-car-sim/releases)
at the same time and select the "PID Control" project.

    $ lein run

Try to choose values for the P, I, and D parameters so that the car drives as smoothly as
possible. Once the car is driving smoothly, try slowly increasing the car's speed to see
how high you can go.

## Solution

You should complete this project on your own first. After you've completed the project,
you can review
[my solution](https://github.com/ericlavigne/CarND-PID-Control-Clojure/compare/solution)
to see how it compares.

## License

Copyright © 2018 Eric Lavigne

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
