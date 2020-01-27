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

(defn led-handler [req]
  (println req))

(defn pulse-handler [req]
  (let [body (keywordize-keys (json/read-str (request/body-string req)))]
    (println body)
    ;; (pulse (keyword (:pin-tag body))
    ;;        (Integer/parseInt (:wait-ms body))
    ;;        (Integer/parseInt (:num-pulses body)))
    (content-type (response/response "<h1>Success.</h1>") "text/html")))

(defn blink [pin_num]
  (try
    (let [device (gpio/device 0)
          handle (gpio/handle device
                              {pin_num {::gpio/tag :red}}
                              {::gpio/direction :output})
          buff (gpio/buffer handle)]
      (println "Setting " pin_num " high")
      (gpio/write handle (-> buff (gpio/set-line :red true)))
                                        ;   (println (gpio/describe-line device 17))
      (Thread/sleep 2000)
      (println "Setting " pin_num " low")
      (gpio/write handle (-> buff (gpio/set-line :red false)))
                                        ;    (println (gpio/describe-line device 17))
      (gpio/close handle)
      (gpio/close device))
    (catch Exception e
      (println e))))

(defn blink-handler [req]
  (let [body (keywordize-keys (json/read-str (request/body-string req)))]
    (println body)
    (blink (:port body))
    (content-type (response/response "<h1>Success.</h1>") "text/html"))
  )

(def api-routes
  ["/" [["led" led-handler]
        ["blink" blink-handler]
        ["pin" pin-handler]
        ["pulse" pulse-handler]
        ["ip" get-ip-address-handler]
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
