(ns myshelf.twitter
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [langohr.basic :as lb]
            [langohr.channel :as lch]
            [langohr.core :as lc]
            [langohr.consumers :as lcs]
            [langohr.queue :as lq]
            [myshelf.models.user :as user]
            [myshelf.worker :refer [connection-params
                                    default-exchange
                                    handle-message
                                    reply-queue
                                    worker-queue]]
            [twitter.oauth :as auth]
            [twitter.api.restful :as twitter])
  (:gen-class))

(defn get-creds
  []
  (auth/make-oauth-creds (System/getenv "TWITTER_APP_KEY")
                         (System/getenv "TWITTER_APP_SECRET")
                         (System/getenv "TWITTER_USER_TOKEN")
                         (System/getenv "TWITTER_USER_SECRET")))

(defn make-command
  [tweet]
  (let [screen-name (-> tweet
                        :user
                        :screen_name)
        [cmd args] (-> (:text tweet)
                       (str/split #":" 2)
                       second
                       str/trim
                       (str/split #" " 2))
        trimmed-args (first (str/split args #"\n"))]
    (if (= "add" cmd)
      {:user-handle screen-name
       :cmd cmd
       :args (vec (str/split trimmed-args #" by "))}
      {:user-handle screen-name
       :cmd cmd
       :args [trimmed-args]})))

(defn send-command
  [channel tweet]
  (let [cmd (make-command tweet)]
    (lb/publish channel default-exchange worker-queue
                (json/generate-string cmd))))

(defn valid-command?
  [tweet]
  (->> tweet
       :text
       (re-find #"^myshelf-bot: (add|rank) ((\w|-+) ?)+$")
       first
       boolean))

(defn check-tweets
  [channel creds screen-name current-id]
  (let [tweets (twitter/statuses-user-timeline
                :oauth-creds creds
                :params (merge {:screen-name screen-name
                                :count 10}
                               (when current-id
                                 {:since_id current-id})))
        last-seen-id (:id (first (:body tweets)))]
    (->> tweets
         :body
         (sort-by :id)
         (filter valid-command?)
         (map (partial send-command channel))
         doall)
    last-seen-id))

(defn trim-title
  [title]
  (if (> (count title) 30)
    (str (subs title 0 25) "...")
    title))

(defn book->string
  [{:keys [id title author]}]
  (format "%s|%s|%s" id (trim-title title) author))

(defn generate-status
  [{:keys [msg url sent-cmd sent-args results]}]
  (cond
    (or msg url) (str msg ":" url)
    (= "rank-books" sent-cmd) (->> results
                                   (take 4)
                                   (map (comp trim-title :title))
                                   (str/join "\n"))
    (= "add-book" sent-cmd) (if results
                              (format "Added %s to %s"
                                      (first sent-args)
                                      (last sent-args))
                              (format "Could not add %s to %s"
                                      (first sent-args)
                                      (last sent-args)))))

(defn handle-reply
  [creds channel metadata body]
  (let [parsed-body (json/parse-string (String. body "UTF-8") true)
        status (generate-status parsed-body)]
    (try (twitter/statuses-update :oauth-creds creds
                                  :params
                                  {:status (str "@"
                                                (:user-handle parsed-body)
                                                "\n"
                                                status)})
         (catch Exception ex
           (println "error attempting to post status" status)
           (println (.getMessage ex))))))

(defn listen-for-tweets
  [channel creds screen-name check-interval]
  (loop [ch channel
         current-id (:last_tweet (user/find-by-handle screen-name))]
    (let [last-id (check-tweets ch creds screen-name current-id)
          next-id (or last-id current-id)]
      (user/update-last-tweet screen-name next-id)
      (Thread/sleep check-interval)
      (recur ch next-id))))

(defn -main [& args]
  (let [conn (lc/connect connection-params)
        channel (lch/open conn)
        screen-name "mindbat"
        creds (get-creds)
        check-interval (* 60 1000) #_"one minute"
        goodreads-creds (atom {})]
    (lq/declare channel worker-queue {:auto-delete false})
    (lq/declare channel reply-queue {:auto-delete false})
    (lcs/subscribe channel worker-queue (partial handle-message
                                                 goodreads-creds)
                   {:auto-ack true})
    (lcs/subscribe channel reply-queue (partial handle-reply creds)
                   {:auto-ack true})
    (listen-for-tweets channel creds screen-name check-interval)))
