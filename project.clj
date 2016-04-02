(defproject myshelf "0.2.1"
  :description "Set of functions for accessing goodreads data from clojure"
  :url "http://github.com/mindbat/myshelf"
  :license {:name "MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/tools.nrepl "0.2.12"]
                 [clj-oauth "1.5.4"]
                 [clj-time "0.11.0"]
                 [doric "0.9.0"]
                 [robert/bruce "0.8.0"]
                 [server-socket "1.0.0"]]
  :plugins [[cider/cider-nrepl "0.10.1"]]
  :uberjar-name "myshelf-standalone.jar")
