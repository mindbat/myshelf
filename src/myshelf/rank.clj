(ns myshelf.rank
  (:require [clj-time.core :as t]
            [myshelf.common :refer [try-int]]
            [myshelf.friends :refer [get-friend-ratings-for-book]]))

(def score-keys [:average_rating :publication_year
                 :ratings_count :text_reviews_count
                 :id :title :authors])

(defn avg-rating-weight
  [{:keys [average_rating]}]
  (-> average_rating
      Float/parseFloat
      (/ 5.0)))

(defn pub-date-weight
  [{:keys [publication_year]}]
  (let [current-year (t/year (t/now))
        cutoff 1970.0]
    (-> publication_year
        try-int
        (- cutoff)
        (/ (- current-year cutoff)))))

(defn text-rating-weight
  [{:keys [ratings_count text_reviews_count]}]
  (let [num-ratings (Integer/parseInt ratings_count)
        num-text (Integer/parseInt text_reviews_count)]
    (try
      (/ num-text num-ratings)
      (catch Exception ex
        1))))

(defn friend-ratings-weight
  [{:keys [friend-ratings]}]
  (if (> (count friend-ratings) 0)
    (/ (apply + friend-ratings)
       (count friend-ratings))
    1))

(defn compute-book-score
  [book]
  (* (avg-rating-weight book)
     (pub-date-weight book)
     (text-rating-weight book)
     (friend-ratings-weight book)))

(defn score-book
  [consumer access-token friends book]
  (let [bare-book (select-keys book score-keys)
        ratings (get-friend-ratings-for-book consumer access-token
                                             (:id bare-book)
                                             :friends friends)
        rated-book (assoc bare-book :friend-ratings ratings)]
    (assoc rated-book :score (compute-book-score rated-book))))

(defn score-books
  [consumer access-token books friends]
  (map (partial score-book consumer access-token friends) books))

(defn rank-books
  [consumer access-token books friends]
  (->> (score-books consumer access-token books friends)
       (sort-by :score >)
       (map (juxt :id :title (comp :name :author :authors)))
       (map (partial zipmap [:id :title :author]))))
