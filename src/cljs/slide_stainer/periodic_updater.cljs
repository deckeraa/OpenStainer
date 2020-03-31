(ns slide-stainer.periodic-updater
  "Defines functions that sends out queries to keep the client in sync with the server.
  The query can vary based upon the active screen."
  (:require
   [reagent.core :as reagent]
   [devcards.core]
   [cljs-http.client :as http]
   [clojure.edn :as edn]
   [slide-stainer.graphql :as graphql]
   [slide-stainer.procedure-edit]
   [slide-stainer.procedure-selection]
   [slide-stainer.procedure-run])
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]
   [devcards.core :refer [defcard defcard-rg]]))

(defn periodic-updater
  "Defines a function that sends out periodic queries to keep the client in sync with the server.
  screen-cursor contains the active screen (see slide-stainer.atoms/screen-cursor).
  queries-to-run contains a map with the following structure:
  {:init {:query-fn \"This should be a query runs once to initialize the applications state
          :handler-fn \"This should be a handler function that uses the results of query-fn to update state.
          :should-run? \"This should be a boolean function with no parameters, where return true indicates that the initialization query should indeed be run. This is used to allow having the init query-fn only run once.\"
   \"A screen's keys go here\" {
                   :query-fn \"A query-fn that updates the particular screen. Only gets run when the screen is active.\"
                   :handler-fn \"Handler for query-fn\"}
   }"
  ([screen-cursor queries-to-run]
   (let [screen (peek @screen-cursor)
         query  (clojure.string/join
                 ","
                 (filter #(not (nil? %))
                         [(let [query-fn    (get-in queries-to-run [:init :query-fn])
                                should-run? (get-in queries-to-run [:init :should-run?])]
                            (when (and query-fn should-run? (should-run?)) (query-fn)))
                          (when-let [query-fn (get-in queries-to-run [screen :query-fn])]  (query-fn))
                          (when-let [query-fn (get-in queries-to-run [:always :query-fn])] (query-fn))]))]
     (println "Running query in periodic-updater: " query " screen: " screen)
     (if (empty? query)
       (js/setTimeout (partial periodic-updater screen-cursor queries-to-run) (* 5 1000))
       ((graphql/graphql-fn {:query query
                             :handler-fn (fn [resp]
                                           (println "periodic-resp: " resp)
                                           (let [f           (get-in queries-to-run [:init :handler-fn])
                                                 should-run? (get-in queries-to-run [:init :should-run?])]
                                             (when (and f should-run? (should-run?))
                                               (f resp)))
                                           (when-let [f (get-in queries-to-run [:always :handler-fn])] (f resp))
                                           (when-let [f (get-in queries-to-run [screen  :handler-fn])] (f resp))
                                           (println "Setting timeout for next periodic-updater.")
                                           (js/setTimeout (partial periodic-updater screen-cursor queries-to-run) (* 5 1000))
                                           )}))))))

(defn fast-rest-updater
  "A faster updater than the periodic updater. This is used primarily to handle updating the seconds remaining in a procedure."
  ([screen-cursor queries-to-run]
   (let [screen (peek @screen-cursor)
         run-query-fn (get-in queries-to-run [screen :run-query-fn])
         query-fn (get-in queries-to-run [screen :rest-fn])
         handler-fn (get-in queries-to-run [screen :rest-handler-fn])]
     (if (and query-fn run-query-fn (run-query-fn))
       (go (let [raw-resp (<! (query-fn))]
             (let [resp (:body raw-resp)]
               (if handler-fn (handler-fn resp raw-resp))
               (js/setTimeout (partial fast-rest-updater screen-cursor queries-to-run)
                              500))))
       (js/setTimeout (partial fast-rest-updater screen-cursor queries-to-run)
                              500)))))
