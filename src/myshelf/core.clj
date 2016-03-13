(ns myshelf.core
  (:require [clojure.java.io :refer [reader writer]]
            [doric.core :refer [table]]
            [myshelf.auth :refer [find-approval-uri
                                  get-access-token
                                  get-consumer
                                  get-request-token
                                  get-user-id]]
            [myshelf.books :refer [find-book-by-title]]
            [myshelf.friends :refer [get-user-friends]]
            [myshelf.rank :refer [rank-books]]
            [myshelf.reviews :refer [add-book-review]]
            [myshelf.shelves :refer [add-book-to-shelf
                                     find-book-on-shelves
                                     get-all-books-on-shelf]]
            [robert.bruce :refer [try-try-again]]
            [server.socket :as s])
  (:gen-class))

(defn add-book-review-by-title
  [consumer access-token user-id title rating & [review-text]]
  (if-let [shelved-id (:id (first (find-book-on-shelves consumer
                                                        access-token
                                                        user-id
                                                        title)))]
    (add-book-review consumer access-token shelved-id rating
                     review-text)
    "Could not find book on shelves"))

(defn rank-books-on-shelf
  [consumer access-token user-id shelf]
  (let [books (get-all-books-on-shelf consumer access-token
                                      user-id shelf)
        friends (get-user-friends consumer access-token user-id)]
    (rank-books consumer access-token books friends)))

(defn wait-and-access
  [consumer request-token]
  (try-try-again
   {:sleep 1000
    :tries 60}
   get-access-token consumer request-token))

(defn find-book
  [consumer access-token title]
  (table [:id :title :author]
         (find-book-by-title consumer access-token title)))

(defn rank-to-read-books
  [consumer access-token user-id]
  (table [:id :title :author]
         (take 10
               (rank-books-on-shelf consumer access-token
                                    user-id "to-read"))))

(defn export-shelf
  [consumer access-token user-id shelf]
  (let [books (get-all-books-on-shelf consumer access-token
                                      user-id shelf)]
    (->> books
         (map #(assoc % :author (get-in % [:authors :author :name])))
         (table [:title :author])
         (spit (str "goodreads-export-" shelf ".table")))))

(defn handle-socket-command
  [consumer access-token user-id ins outs]
  (binding [*in* (reader ins)
            *out* (writer outs)]
    (try
      (let [incoming (read-line)
            [cmd args] (clojure.string/split incoming #"\|")
            ret (cond
                  (= "find" cmd) (find-book consumer
                                            access-token
                                            args)
                  (= "add" cmd) (add-book-to-shelf consumer access-token
                                                   args "to-read")
                  (= "rank" cmd) (rank-to-read-books consumer access-token
                                                     user-id)
                  (= "export" cmd) (export-shelf consumer
                                                 access-token
                                                 user-id
                                                 args))]
        (println ret))
      (catch Exception ex
        (println "oh noes!" (.getMessage ex))))))

(defn -main
  [& [key secret]]
  (let [consumer (get-consumer key secret)
        request-token (get-request-token consumer)
        approval-uri (find-approval-uri consumer request-token)]
    (println "please hit this url to approve the app:" approval-uri)
    (let [access-token (wait-and-access consumer request-token)
          user-id (get-user-id consumer access-token)]
      (println "starting server socket")
      (s/create-server 6001 (partial handle-socket-command
                                     consumer
                                     access-token
                                     user-id)))))
