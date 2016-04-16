(ns myshelf.test.twitter
  (:require [clojure.test :refer :all]
            [myshelf.twitter :refer :all]))

(deftest t-generate-status
  ;; if have msg or url, just use that
  (is (= "pinfeathers:gollyfluff"
         (generate-status {:msg "pinfeathers"
                           :url "gollyfluff"
                           :results ["ignore" "me"]})))
  ;; rank books should print just first three titles
  (let [ranked [{:id 1 :title "A Wrinkle in Time"}
                {:id 2 :title "A Wizard of Earthsea"}
                {:id 3 :title "Dune"}
                {:id 4 :title "The Goblin Emperor"}
                {:id 5 :title "Redshirts"}]
        status (generate-status {:sent-cmd "rank-books"
                                 :sent-args ["ignored"]
                                 :results ranked})]
    (is (.contains status (:title (first ranked))))
    (is (.contains status (:title (second ranked))))
    (is (.contains status (:title (nth ranked 2))))
    (is (not (.contains status (:title (nth ranked 3)))))
    (is (not (.contains status (:title (last ranked))))))
  (let [add-results {:random "text"}
        add-sent-args ["random-number" "to-read"]]
    ;; add book should report success if any results
    (is (= "Added random-number to to-read"
           (generate-status {:sent-cmd "add-book"
                             :results add-results
                             :sent-args add-sent-args})))
    ;; add book should report failure if no results
    (is (= "Could not add random-number to to-read"
           (generate-status {:sent-cmd "add-book"
                             :results nil
                             :sent-args add-sent-args}))))
  (let [find-results [{:id 1 :title "Full Fathom Five"
                       :author "Max Gladwell"}
                      {:id 2 :title "The Bone Clocks"
                       :author "David Mitchell"}
                      {:id 3 :title "Footsteps in the Sky"
                       :author "Greg Keyes"}]
        status (generate-status {:sent-cmd "find-book"
                                 :results find-results})]
    ;; find book should report id and title and author
    (is (.contains status "1|Full Fathom Five|Max Gladwell"))
    (is (.contains status "2|The Bone Clocks|David Mitchell"))
    ;; find book should only report 2 results
    (is (not (.contains status "3|Footsteps in the Sky|Greg Keyes")))))
