(ns myshelf.books
  (:require [myshelf.auth :refer [make-auth-request-GET]]
            [myshelf.common :refer [build-url
                                    element->map]]))

(defn find-book-by-title
  "Runs a query for a book by title. Returns only the last
  20 results."
  [consumer access-token title]
  (let [search-url (build-url "search" "index")
        params {:q title
                :format "xml"
                :field "title"}
        resp (make-auth-request-GET consumer
                                    access-token
                                    search-url
                                    params)]
    (->> resp
         :content
         second
         :content
         (filter #(= :results (:tag %)))
         first
         :content
         (map element->map)
         (map (comp (juxt :title :id :author) :best_book :work)))))

(defn find-book-by-title-and-author
  "Runs a query for a book by title and author.
  Returns only the last 20 results."
  [consumer access-token title author]
  (let [search-url (build-url "search" "index")
        params {:q (str title " " author)
                :format "xml"}
        resp (make-auth-request-GET consumer
                                    access-token
                                    search-url
                                    params)]
    (->> resp
         :content
         second
         :content
         (filter #(= :results (:tag %)))
         first
         :content
         (map element->map)
         (map (comp (juxt :title :id :author) :best_book :work)))))
