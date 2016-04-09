(ns myshelf.shelves
  (:require [myshelf.auth :refer [make-auth-request-GET
                                  make-auth-request-POST]]
            [myshelf.common :refer [build-url
                                    element->map]]))

(defn extract-book-data
  "Given a <book> in a <review>, pull out the book's info into
  a more useful hashmap"
  [review-element]
  (->> review-element
       :content
       (filter #(= :book (:tag %)))
       first
       :content
       (reduce (fn [book-map book-tag]
                 (merge book-map (element->map book-tag)))
               {})))

(defn get-books-on-shelf
  "Returns list of hashmaps representing the books on a given
  user's bookshelf"
  [consumer access-token user-id shelf & {:keys [query page per-page]}]
  (let [shelf-url (build-url "review" "list" user-id)
        params (merge {:shelf shelf :format "xml" :v 2}
                      (when query
                        {(keyword "search[query]")
                         query})
                      (when page
                        {:page page})
                      (when per-page
                        {:per_page per-page}))
        resp (make-auth-request-GET consumer
                                    access-token
                                    shelf-url
                                    params)]
    (->> resp
         :content
         second
         :content
         (map extract-book-data))))

(defn get-all-books-on-shelf
  "Fetches all the books on a shelf. Note that this might be
  hundreds or thousands of books!"
  [consumer access-token user-id shelf]
  (let [per-page 200]
    (loop [page 1
           books []]
      (let [new-books (get-books-on-shelf consumer access-token
                                          user-id shelf
                                          :page page
                                          :per-page per-page)]
        (if (empty? new-books)
          books
          (recur (inc page) (apply conj books new-books)))))))

(defn find-book-on-shelves
  "Try to find a book on a user's shelves, given book title"
  [consumer access-token user-id title]
  (let [shelf-url (build-url "review" "list" user-id)
        params {:format "xml" :v 2 (keyword "search[query]") title}
        resp (make-auth-request-GET consumer
                                    access-token
                                    shelf-url
                                    params)]
    (->> resp
         :content
         second
         :content
         (map extract-book-data))))

(defn add-book-to-shelf
  [consumer access-token book-id shelf]
  (let [add-url (build-url "shelf" "add_to_shelf.xml")
        params {:name shelf
                :book_id book-id}]
    (try
      (make-auth-request-POST consumer
                              access-token
                              add-url
                              params)
      (catch Exception ex
        nil))))
