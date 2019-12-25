(ns test-reagent.core
  (:require [dvlopt.linux.gpio :as gpio]
            [bidi.bidi :as bidi]
            [bidi.ring :refer [make-handler]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.util.response :as response :refer [file-response content-type]]
            [ring.util.request :as request]
            [clojure.data.json :as json]
            [clojure.walk :refer [keywordize-keys]])
  (:gen-class))

(defn led-handler [req]
  (println req))

(defonce state-atom (atom {}))

(defn init-pins
  ([] (init-pins state-atom))
  ([state-atom]
   (swap! state-atom assoc :device (gpio/device 0))
   (swap! state-atom assoc :handle (gpio/handle (:device @state-atom)
                                                {17 {::gpio/tag :ena}
                                                 18 {::gpio/tag :dir}
                                                 19 {::gpio/tag :pul}}
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
        [true (fn [req] (content-type (response/response "<h1>Hi from Pi.</h1>") "text/html"))]]])

(def app
  (-> (make-handler api-routes)
      (wrap-cors
       :access-control-allow-origin [#".*"]
       :access-control-allow-methods [:get :put :post :delete]
       :access-control-allow-credentials ["true"]
       :access-control-allow-headers ["X-Requested-With","Content-Type","Cache-Control"])))
