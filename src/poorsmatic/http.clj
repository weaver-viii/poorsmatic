(ns poorsmatic.http
  (:require [clj-http.client :as client]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [immutant.cache :as cache]))

(defn scrape*
  "Returns a hashed response given a url"
  [url]
  (try
    (log/info "Fetching" url)
    (time (client/get url {:socket-timeout 10000 :conn-timeout 10000}))
    (catch Exception e (log/warn (str e)) {})))
(def scrape (cache/memo scrape* "scraped" :idle 10))

(defn counter
  "Adds a :count to the response"
  [word]
  (fn [m]
    (assoc m :count (-> (str/lower-case (:body m ""))
                        (str/split #"[^\w]+")
                        frequencies
                        (get (str/lower-case word) 0)))))

(defn title
  "Adds a :title to the response"
  [m]
  (assoc m :title (last (re-find #"<title>(.*?)</title>" (:body m "")))))

(defn url
  "Adds a :url to the response"
  [m]
  (assoc m :url (last (:trace-redirects m))))

(defn saver
  [path]
  (fn [m]
    (if-let [url (first (:trace-redirects m))]
      (spit (str path (clj-http.util/url-encode url)) m))
    m))