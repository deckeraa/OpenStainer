(ns slide-stainer.graphql
  (:require
   [reagent.core :as reagent]
   [devcards.core]
   [cljs-http.client :as http]
   [clojure.edn :as edn]
   )
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]
   [devcards.core :refer [defcard defcard-rg]]))

(def procedure-keys
;;  "Contains a comma-delimited string of all keys in the procedure object"
  "_id,_rev,type,name,jar_contents,procedure_steps{substance,time_in_seconds,jar_number}"
  )

(defn graphql-fn [{query :query query-fn :query-fn handler-fn :handler-fn variable-fn :variable-fn :as args}]
  (fn []
    (go (let [raw-resp (<! (http/post "http://localhost:3000/graphql"
                                      {:json-params {:query (or (if query-fn (query-fn) nil)
                                                                query)
                                                     :variables (if variable-fn (variable-fn) nil)}}
                                       :with-credentials? false))
               resp (:data (edn/read-string (:body raw-resp)))]
           (println "resp: " resp)
           (println "raw-resp: " raw-resp)
           (println "handler-fn " handler-fn)
           (if handler-fn (handler-fn resp raw-resp))))))
