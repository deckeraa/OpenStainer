(ns slide-stainer.core
  (:require [dvlopt.linux.gpio :as gpio]
            [bidi.bidi :as bidi]
            [bidi.ring :refer [make-handler]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.util.response :as response :refer [file-response content-type]]
            [ring.util.request :as request]
            [clojure.data.json :as json]
            [clojure.walk :refer [keywordize-keys]]
            [clojure.java.shell :refer [sh]]
            [clojure.java.io :as io]
            [com.walmartlabs.lacinia.util :as util]
            [com.walmartlabs.lacinia.schema :as schema]
            [com.walmartlabs.lacinia :as lacinia]
            [clojure.edn :as edn]
            [clojure.walk :as walk])
  (:use clojure.test)
  (:import (clojure.lang IPersistentMap))
  (:gen-class))

(defonce state-atom (atom {}))

(def pin-defs
  {:stepperX {:pins
              {17 {::gpio/tag :stepperX-ena
                   :inverted? true}
               18 {::gpio/tag :stepperX-dir
                   :inverted? true}
               19 {::gpio/tag :stepperX-pul
                   :inverted? true}}
              :pos nil
              :pos-limit-inches 9}
   :led13 {:pins
           {13 {::gpio/tag :led13-led}}}})

(with-test
  (defn index-pin-defs [pin-defs]
    (apply merge
           (map (fn [[device {pins :pins}]]
                  (as-> pins $
                    (map (fn [[pin_number {tag :dvlopt.linux.gpio/tag inverted? :inverted?}]]
                           {tag {:inverted? inverted? :pin_number pin_number :device device}}) $)
                    (apply merge $))) pin-defs)))
  (let [sample-pin-defs {:stepperX {:pins
                                    {17 {::gpio/tag :stepperX-ena
                                         :inverted? true}
                                     18 {::gpio/tag :stepperX-dir
                                         :inverted? true}
                                     19 {::gpio/tag :stepperX-pul
                                         :inverted? true}}
                                    :pos nil
                                    :pos-limit-inches 9}
                         :stepperZ {:pins
                                    {20 {::gpio/tag :stepperZ-ena
                                         :inverted? true}
                                     21 {::gpio/tag :stepperZ-dir
                                         :inverted? true}
                                     22 {::gpio/tag :stepperZ-pul
                                         :inverted? true}}
                                    :pos nil
                                    :pos-limit-inches 4}}
        sample-index {:stepperX-ena {:pin_number 17 :inverted? true :device :stepperX}
                      :stepperX-dir {:pin_number 18 :inverted? true :device :stepperX}
                      :stepperX-pul {:pin_number 19 :inverted? true :device :stepperX}
                      :stepperZ-ena {:pin_number 20 :inverted? true :device :stepperZ}
                      :stepperZ-dir {:pin_number 21 :inverted? true :device :stepperZ}
                      :stepperZ-pul {:pin_number 22 :inverted? true :device :stepperZ}}]
    (is (= sample-index (index-pin-defs sample-pin-defs)))))

(with-test
  (defn get-pin-defs-for-gpio-lib [pin-defs]
    (apply merge (map (fn [[device {pins :pins}]]
                        (apply merge (map (fn [[pin_num {tag ::gpio/tag}]]
                                            {pin_num {::gpio/tag tag}}) pins))) pin-defs)))
  (let [sample-pin-defs {:stepperX {:pins
                                    {17 {::gpio/tag :stepperX-ena
                                         :inverted? true}
                                     18 {::gpio/tag :stepperX-dir
                                         :inverted? true}
                                     19 {::gpio/tag :stepperX-pul
                                         :inverted? true}}
                                    :pos nil
                                    :pos-limit-inches 9}
                         :stepperZ {:pins
                                    {20 {::gpio/tag :stepperZ-ena
                                         :inverted? true}
                                     21 {::gpio/tag :stepperZ-dir
                                         :inverted? true}
                                     22 {::gpio/tag :stepperZ-pul
                                         :inverted? true}}
                                    :pos nil
                                    :pos-limit-inches 4}
                         :led12 {:pins
                                 {12 {::gpio/tag :led12-led
                                      :inverted? true}}}}
        pin-defs-for-lib {12 {::gpio/tag :led12-led}
                          17 {::gpio/tag :stepperX-ena}
                          18 {::gpio/tag :stepperX-dir}
                          19 {::gpio/tag :stepperX-pul}
                          20 {::gpio/tag :stepperZ-ena}
                          21 {::gpio/tag :stepperZ-dir}
                          22 {::gpio/tag :stepperZ-pul}}]
    (is (= pin-defs-for-lib (get-pin-defs-for-gpio-lib sample-pin-defs)))))

(defn init-pins
  ([] (init-pins state-atom))
  ([state-atom]
   (swap! state-atom assoc :device (gpio/device 0))
   (swap! state-atom assoc :handle (gpio/handle (:device @state-atom)
                                                (get-pin-defs-for-gpio-lib (:setup @state-atom))
                                                {::gpio/direction :output}))
   (swap! state-atom assoc :buffer (gpio/buffer (:handle @state-atom)))))

(defn clean-up-pins
  ([] (clean-up-pins state-atom))
  ([state-atom]
   (gpio/close (:handle @state-atom))
   (gpio/close (:device @state-atom))
   (swap! state-atom dissoc :handle)
   (swap! state-atom dissoc :device)
   (swap! state-atom dissoc :buffer)))

(swap! state-atom assoc :setup pin-defs)
(swap! state-atom assoc :setup-index (index-pin-defs pin-defs))

(with-test
  (defn normalize-pin-tag [tag]
    "Take tag names and convert them to a keyword consistently."
    (keyword (clojure.string/replace tag #"^:" "")))
  (is (= (normalize-pin-tag ":foo") :foo))
  (is (= (normalize-pin-tag "foo")  :foo)))

(defn resolve-pin-by-id [context args value]
  (when (not (:device @state-atom)) (init-pins))
  (let [id (normalize-pin-tag (:id args))
        pin-info (id (:setup-index @state-atom))
        board_value (gpio/get-line (:buffer @state-atom) id)]
    {:board_value board_value
     :logical_value (if (:inverted? pin-info) (not board_value) board_value)
     :pin_number (:pin_number pin-info)
     }))

(defn get-ip-address []
  (as-> (sh "ifconfig" "wlan0") $
      (:out $)
      (clojure.string/split-lines $) ; split up the various lines of ifconfig output
      (map #(re-find #"inet\s+\d+\.\d+\.\d+\.\d+" %) $) ; find things matching the inet ip
      (filter (complement nil?) $) ; filter out the non-matching lines
      (first $) ; grab the first one (there should only be one
      (clojure.string/replace $ #"inet\s+" "") ; take out the inet portion
      ))

(defn resolve-ip [context args value]
;  (println (sh "ifconfig" "wlan0"))
;  (println (str "resolve-ip: " (get-ip-address)))
  {:inet4 (get-ip-address)
   })

(defn simplify
  "Converts all ordered maps nested within the map into standard hash maps, and
   sequences into vectors, which makes for easier constants in the tests, and eliminates ordering problems."
  [m]
  (walk/postwalk
    (fn [node]
      (cond
        (instance? IPersistentMap node)
        (into {} node)

        (seq? node)
        (vec node)

        :else
        node))
    m))


(defn resolver-map []
  {:query/pin_by_id resolve-pin-by-id
   :query/ip resolve-ip})

(defn load-schema
  []
  (-> "schema.edn"
      slurp
      edn/read-string
      (util/attach-resolvers (resolver-map))
      schema/compile))

(def schema (load-schema))

(defn simplify
  "Converts all ordered maps nested within the map into standard hash maps, and
   sequences into vectors, which makes for easier constants in the tests, and eliminates ordering problems. https://lacinia.readthedocs.io/en/latest/tutorial/game-data.html"
  [m]
  (walk/postwalk
    (fn [node]
      (cond
        (instance? IPersistentMap node)
        (into {} node)

        (seq? node)
        (vec node)

        :else
        node))
    m))

(defn q
  [query-string]
  (-> (lacinia/execute schema query-string nil nil)
      (simplify)))

(defn get-ip-address-handler [req]
  (content-type (response/response (get-ip-address)) "text/html"))

(defn graphql-handler [req]
  (let [body (keywordize-keys (json/read-str (request/body-string req)))]
    (println "graphql query: " body)
    (content-type (response/response (str (q (:query body)))) "text/html"))) 

(defn led-handler [req]
  (println req))

(defn set-pin [pin-tag state]
  (when (not (:device @state-atom)) (init-pins))
  (gpio/write (:handle @state-atom) (-> (:buffer @state-atom) (gpio/set-line pin-tag state))))

(defn pin-handler [req]
  (let [body (keywordize-keys (json/read-str (request/body-string req)))]
    (println body)
    (set-pin (keyword (:pin-tag body)) (:state body))
    (content-type (response/response "<h1>Success.</h1>") "text/html"))
  )

(defn pulse [pin-tag wait-ms num-pulses]
  (loop [i num-pulses]
    (when (> i 0)
      (set-pin pin-tag false)
      (java.util.concurrent.locks.LockSupport/parkNanos (* 1000 wait-ms))
      (set-pin pin-tag true)
      (java.util.concurrent.locks.LockSupport/parkNanos (* 1000 wait-ms))
;      (Thread/sleep wait-ms)
      (recur (- i 1)))))

(defn pulse-handler [req]
  (let [body (keywordize-keys (json/read-str (request/body-string req)))]
    (println body)
    (pulse (keyword (:pin-tag body))
           (Integer/parseInt (:wait-ms body))
           (Integer/parseInt (:num-pulses body)))
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

(defn alternating-leds
  "Alternates leds.
   In the REPL, press CTRL-C in order to stop.
   Ex. (alternate {:device       0
                   :interval-ms  250
                   :line-numbers [17 27 22]})"
  ([line-numbers]
   (alternating-leds line-numbers
                     nil))
  ([line-numbers {:as   options
                  :keys [device
                         interval-ms]
                  :or   {device      0
                         interval-ms 500}}]
   (with-open [device' (gpio/device device)
               handle  (gpio/handle device'
                                    (reduce (fn add-led [line-number->line-options line-number]
                                              (assoc line-number->line-options
                                                     line-number
                                                     {::gpio/state false}))
                                            {}
                                            line-numbers)
                                    {::gpio/direction :output})]
     (let [buffer (gpio/buffer handle)]
       (loop [line-numbers' (cycle line-numbers)]
         (gpio/write handle
                     (-> buffer
                         gpio/clear-lines
                         (gpio/set-line (first line-numbers')
                                        true)))
         (Thread/sleep interval-ms)
         (recur (rest line-numbers')))))))

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
