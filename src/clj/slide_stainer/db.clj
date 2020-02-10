(ns slide-stainer.db
  (:require [slide-stainer.defs :refer :all]
            [com.ashafa.clutch :as couch]
            [clojure.spec.alpha :as spec]
            [cemerick.url]
            [clj-time.core :as time]
            [clj-time.format]
            [clj-time.predicates]
            [clj-time.periodic]
            [clojure.test :refer :all])
  (:use clojure.test))

(defn gen-db-from-string [db-str]
  "Use like (swap! db gen-db-from-string)"
  (assoc (cemerick.url/url db-str)
         :username "admin"
         :password "test"  ; TODO add real credential handling
         ))

(def db (atom (gen-db-from-string "http://localhost:5984/slide_stainer")))

(defn put-doc [doc]
  (couch/put-document @db doc))
