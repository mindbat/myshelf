(ns myshelf.reviews
  (:require [myshelf.auth :refer [make-auth-request-GET
                                  make-auth-request-POST
                                  make-auth-request-PUT]]
            [myshelf.common :refer [build-url]]))

(defn get-book-review
  [consumer access-token user-id book-id]
  (let [review-url (build-url "review"
                              "show_by_user_and_book.xml")
        params {:user_id user-id
                :book_id book-id}
        resp (make-auth-request-GET consumer
                                    access-token
                                    review-url
                                    params)]
    (-> resp
        :content
        second
        :content)))

(defn get-book-rating
  "Get a user's rating for a given book"
  [consumer access-token user-id book-id]
  (try (->> (get-book-review consumer access-token user-id book-id)
            (filter #(= :rating (:tag %)))
            first
            :content
            first)
       (catch Exception ex
         nil)))

(defn add-book-review
  "Add a book review. Automatically adds book to read shelf."
  [consumer access-token book-id rating & [review-text]]
  (let [review-url (build-url "review.xml")
        params (merge {:format "xml"
                       :book_id book-id
                       (keyword "review[rating]") rating
                       :shelf "read"}
                      (when review-text
                        {(keyword "review[review]") review-text}))]
    (make-auth-request-POST consumer
                            access-token
                            review-url
                            params)))

(defn edit-book-review
  "Edit a book review. Automatically sets book to finished
  and adds it to the read shelf, if it wasn't already there."
  [consumer access-token review-id new-rating & [review-text]]
  (let [review-url (build-url "review" (str review-id ".xml"))
        params (merge {:id review-id
                       (keyword "review[rating]") new-rating
                       :finished true
                       :shelf "read"}
                      (when review-text
                        {(keyword "review[review]") review-text}))]
    (make-auth-request-PUT consumer
                           access-token
                           review-url
                           params)))
