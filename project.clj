(defproject myshelf "0.2.1"
  :description "Set of functions for accessing goodreads data from clojure"
  :url "http://github.com/mindbat/myshelf"
  :license {:name "MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/java.jdbc "0.4.1"]
                 [org.postgresql/postgresql "9.4-1201-jdbc41"]
                 [org.clojure/tools.nrepl "0.2.12"]
                 [cheshire "5.5.0"]
                 [clj-oauth "1.5.4"]
                 [clj-time "0.11.0"]
                 [com.novemberain/langohr "3.5.1"]
                 [doric "0.9.0"]
                 [robert/bruce "0.8.0"]
                 [server-socket "1.0.0"]
                 [twitter-api "0.7.8"]]
  :plugins [[cider/cider-nrepl "0.10.1"]]
  :min-lein-version "2.5.3"
  :uberjar-name "myshelf-standalone.jar")
