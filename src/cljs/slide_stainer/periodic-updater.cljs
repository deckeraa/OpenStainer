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

(defn periodic-updater [screen-cursor queries-to-run]
  (println "Top of periodic-updater: " @screen-cursor)
  (let [refresh-fn (get queries-to-run @screen-cursor)]
    (when refresh-fn (refresh-fn)))
  (js/setTimeout (partial periodic-updater screen-cursor queries-to-run) (* 5 1000)))
