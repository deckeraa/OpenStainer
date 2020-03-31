(ns slide-stainer.graphql
  "GraphQL functions for the slide stainer"
  (:require
   [reagent.core :as reagent]
   [cljs-http.client :as http]
   [devcards.core :refer-macros [deftest defcard-rg]]
   [cljs.test :refer-macros [is testing run-tests]]
   [clojure.edn :as edn]
   )
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]
   [devcards.core :refer [defcard defcard-rg]]))

(def number-of-jars 6)
(def empty-procedure {:type "procedure"
                      :jarContents (vec (repeat number-of-jars " "))
                      :procedureSteps []
                      :name " "
                      :repeat 1})

(def procedure-keys
;;  "Contains a comma-delimited string of all keys in the procedure object"
  "_id,_rev,type,name,jarContents,procedureSteps{substance,timeInSeconds,jarNumber},repeat"
  )

(def run-status-keys
  "currentProcedureStepNumber,currentCycleNumber")

(def settings-keys "_id,_rev,developer")

(defn remove-quotes-from-keys
  "This removes quotes from the keyword in a JSON string to make it compatible with GraphQL."
  [s]
  (clojure.string/replace s #"\"(\w+)\":" "$1:"))

(deftest remove-quotes-from-keys-test
  (is (= (remove-quotes-from-keys "{\"name\":\"foo\"}") "{name:\"foo\"}")))

(defn jsonify [s]
  (.stringify js/JSON (clj->js s)))

(defn graphql-fn
  "Runs a GraphQL query and handles the results. You can pass a query as either a string query or as a fn returning a string"
  [{query :query query-fn :query-fn handler-fn :handler-fn variable-fn :variable-fn :as args}]
  (fn []
    (println (or (if query-fn (query-fn) nil) query))
    (go (let [raw-resp (<! (http/post "http://localhost:8000/graphql"
                                      {:json-params {:query (or (if query-fn (query-fn) nil)
                                                                query)
                                                     :variables (if variable-fn (variable-fn) nil)}}
                                       :with-credentials? false))]
          (println "raw-resp: " raw-resp)
          (let [resp (:data (:body raw-resp))]
            ;; (println "resp: " resp)
            (if handler-fn (handler-fn resp raw-resp)))))))
