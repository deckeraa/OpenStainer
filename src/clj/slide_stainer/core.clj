(ns slide-stainer.core
  (:require [dvlopt.linux.gpio :as gpio]
            [bidi.bidi :as bidi]
            [bidi.ring :refer [make-handler]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.util.response :as response :refer [file-response content-type]]
            [ring.util.request :as request]
            [clojure.data.json :as json]
            [clojure.walk :refer [keywordize-keys]]
            [clojure.java.io :as io]
            [com.walmartlabs.lacinia.util :as util]
            [com.walmartlabs.lacinia.schema :as schema]
            [com.walmartlabs.lacinia :as lacinia]
            [clojure.edn :as edn]
            [clojure.core.async :as async :refer [go go-loop <! timeout thread chan mult tap put!]]
            [incanter.core :refer [pow]]
            [slide-stainer.defs :refer :all]
            [slide-stainer.board-setup :refer :all]
            [slide-stainer.motion :refer :all]
            [slide-stainer.graphql :refer :all])
  (:use clojure.test)
  (:gen-class))

(defonce buid-pin-defs-placeholder
  (do (swap! state-atom assoc :setup pin-defs)
      (swap! state-atom assoc :setup-index (index-pin-defs pin-defs))))

(defn pin-handler [req]
  (let [body (keywordize-keys (json/read-str (request/body-string req)))]
    (println body)
    (set-pin (keyword (:pin-tag body)) (:state body))
    (content-type (response/response "<h1>Success.</h1>") "text/html"))
  )

(defn get-ip-address-handler [req]
  (content-type (response/response (get-ip-address)) "text/html"))

(defn graphql-handler [req]
  (let [body (keywordize-keys (json/read-str (request/body-string req)))]
    (println "graphql query: " body)
    (content-type (response/response (str (q (:query body) (:variables body)))) "text/html")))

(def api-routes
  ["/" [["ip" get-ip-address-handler]
        ["graphql" graphql-handler]
        ["cleanup" (fn [req]
                     (clean-up-pins)
                     (content-type (response/response "Pins cleaned up") "text/html"))]
        [true (fn [req] (content-type (response/response "<h1>Hi from Pi.</h1>") "text/html"))]]])

(def app
  (-> (make-handler api-routes)
      (wrap-cors
       :access-control-allow-origin [#".*"]
       :access-control-allow-methods [:get :put :post :delete]
       :access-control-allow-credentials ["true"]
       :access-control-allow-headers ["X-Requested-With","Content-Type","Cache-Control"])))
