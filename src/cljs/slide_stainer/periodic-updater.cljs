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
   (let [screen (peek @screen-cursor)]
                                        ;    (println "screen: " screen)
     (let [query-fn    (get-in queries-to-run [screen :query-fn])
           anim-fn (get-in queries-to-run [screen :anim-fn])
           always-fn (get-in queries-to-run [:always :query-fn])]
       (when (and query-fn (= 0 seconds)) (query-fn))
       (when anim-fn (anim-fn))
       (when always-fn (always-fn)))
     (js/setTimeout (partial periodic-updater screen-cursor queries-to-run (mod (inc seconds) 5)) (* 1 1000)))))
