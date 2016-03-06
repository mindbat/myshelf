(ns myshelf.common
  (:require [clojure.string :as str]))

(def goodreads-base-url "https://www.goodreads.com")

(defn build-url
  [& parts]
  (str/join "/" (apply conj [goodreads-base-url] parts)))

(defn element->map
  "Convert a clojure.data.xml Element into a hashmap"
  [element]
  {(:tag element)
   (if (string? (first (:content element)))
     (first (:content element))
     (apply merge (map element->map (:content element))))})

(defn try-int
  [val]
  (try
    (Integer/parseInt val)
    (catch Exception ex
      0)))
