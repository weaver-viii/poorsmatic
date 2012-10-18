(ns poorsmatic.tweets
  (:require [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [clojure.string :as str]
            [immutant.daemons :as dmn]
            [immutant.messaging :as msg]
            [twitter.oauth :as oauth]
            [twitter.callbacks.handlers :as handler]
            [twitter.api.streaming :as stream]
            [http.async.client :as ac])
  (:import twitter.callbacks.protocols.AsyncStreamingCallback))

(def config-endpoint "/topic/configure/tweets")

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

(defn ^:private filter-tweets
  "Invoke a twitter-api streaming connection for a comma-delimited statuses filter string"
  [filter handler]
  (let [callback (AsyncStreamingCallback. (handle handler)
                                          (comp println handler/response-return-everything)
                                          handler/exception-print)]
    (stream/statuses-filter :params {:track filter}
                            :client (ac/create-client :request-timeout -1) ; TODO: not this
                            :oauth-creds creds
                            :callbacks callback)))

(defn ^:private make-config-handler
  [stream handler]
  (fn [terms]
    (let [stop (:cancel (meta @stream))
          filter (str/join "," terms)]
      (if stop (stop))
      (log/info "Tweets filter:" filter)
      (if (empty? terms)
        (reset! stream nil)
        (reset! stream (filter-tweets filter handler))))))

(defn configure [terms]
  (msg/publish config-endpoint terms))

(defn daemon
  "Start the tweets service"
  [handler]
  (let [tweets (atom nil)
        configurator (atom nil)]
    (dmn/daemonize "tweets"
                   (reify dmn/Daemon
                     (start [_]
                       (log/info "Starting tweets service")
                       (msg/start config-endpoint)
                       (reset! configurator
                               (msg/listen config-endpoint (make-config-handler tweets handler))))
                     (stop [_]
                       (if @tweets ((:cancel (meta @tweets))))
                       (msg/unlisten @configurator)
                       (msg/stop config-endpoint)
                       (log/info "Stopped tweets service"))))))
