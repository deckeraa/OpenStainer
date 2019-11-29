(ns test-reagent.core
  (:require [dvlopt.linux.gpio :as gpio]
            [bidi.bidi :as bidi]
            [bidi.ring :refer [make-handler]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.util.response :as response :refer [file-response content-type]]
            [ring.util.request :as request])
  (:gen-class))

(defn led-handler [req]
  "foo")

(defn blink []
  (let [device (gpio/device 0)
        handle (gpio/handle device
                            {17 {::gpio/tag :red}}
                            {::gpio/direction :output})
        buff (gpio/buffer handle)]
    (println "Setting 17 high")
    (gpio/write handle (-> buff (gpio/set-line :red true)))
    (println (gpio/describe-line device 17))
    (Thread/sleep 2000)
    (println "Setting 17 low")
    (gpio/write handle (-> buff (gpio/set-line :red false)))
    (println (gpio/describe-line device 17))
    (gpio/close handle)
    (gpio/close device)))

(defn blink-handler [req]
  (blink)
  (content-type (response/response "<h1>Success.</h1>") "text/html")
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
        [true (fn [req] (content-type (response/response "<h1>Hi from Pi.</h1>") "text/html"))]]])

(def app
  (-> (make-handler api-routes)
      (wrap-cors
       :access-control-allow-origin [#".*"]
       :access-control-allow-methods [:get :put :post :delete]
       :access-control-allow-credentials ["true"]
       :access-control-allow-headers ["X-Requested-With","Content-Type","Cache-Control"])))
