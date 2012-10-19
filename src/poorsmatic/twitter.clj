(ns poorsmatic.twitter
  (:require [clojure.data.json :as json]
            [twitter.oauth :as oauth]
            [twitter.callbacks.handlers :as handler]
            [twitter.api.streaming :as stream]
            [http.async.client :as ac])
  (:import twitter.callbacks.protocols.AsyncStreamingCallback))

(def
  ^{:doc "twitter oauth credentials"
    :private true}
  creds (apply oauth/make-oauth-creds (read-string (slurp "/tmp/creds"))))

(defn ^:private handle
  "Process a chunk of async tweetness"
  [handler]
  (fn [response baos]
    (try
      (handler (json/read-json (str baos)))
      (catch Throwable ignored))))

(defn filter-tweets
  "Invoke a twitter-api streaming connection for a comma-delimited statuses filter string"
  [filter handler]
  (let [callback (AsyncStreamingCallback. (handle handler)
                                          (comp println handler/response-return-everything)
                                          handler/exception-print)]
    (stream/statuses-filter :params {:track filter}
                            :client (ac/create-client :request-timeout -1) ; TODO: not this
                            :oauth-creds creds
                            :callbacks callback)))
(defn close
  [stream]
  (if stream ((:cancel (meta stream)))))
