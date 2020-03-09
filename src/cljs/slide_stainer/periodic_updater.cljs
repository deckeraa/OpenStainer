(ns slide-stainer.periodic-updater
  (:require
   [reagent.core :as reagent]
   [devcards.core]
   [cljs-http.client :as http]
   [clojure.edn :as edn]
   [slide-stainer.graphql :as graphql]
   [slide-stainer.program-creation]
   [slide-stainer.procedure-selection]
   [slide-stainer.procedure-run])
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]
   [devcards.core :refer [defcard defcard-rg]]))

(defn periodic-updater
  ([screen-cursor queries-to-run]
   (periodic-updater screen-cursor queries-to-run 0))
  ([screen-cursor queries-to-run seconds]
   (let [screen (peek @screen-cursor)
         query  (clojure.string/join
                 ","
                 (filter #(not (nil? %))
                         [(let [query-fn    (get-in queries-to-run [:init :query-fn])
                                should-run? (get-in queries-to-run [:init :should-run?])]
                            (when (and query-fn should-run? (should-run?)) (query-fn)))
                          (when-let [query-fn (get-in queries-to-run [screen :query-fn])]  (query-fn))
                          (when-let [query-fn (get-in queries-to-run [:always :query-fn])] (query-fn))]))]
     (println "Running query in periodic-updater: " query)
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
                                         (js/setTimeout (partial periodic-updater screen-cursor queries-to-run (mod (inc seconds) 5)) (* 5 1000)))})))))
