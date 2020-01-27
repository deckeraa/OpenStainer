(ns slide-stainer.board-setup
  (:require
   [dvlopt.linux.gpio :as gpio]
   [clojure.core.async :as async :refer [go go-loop <! timeout thread chan mult tap put!]]
   [slide-stainer.defs :refer :all])
  (:use clojure.test))

(with-test (defn init-status-atm
             ([gpio-watcher pin-defs]
              (init-status-atm gpio-watcher pin-defs nil))
             ([gpio-watcher pin-defs fetch-fn]
              (let [fetch-fn (if fetch-fn fetch-fn
                                 (fn [tag] (gpio/poll gpio-watcher (gpio/buffer gpio-watcher) tag)))]
                (apply merge (map (fn [[device device-map]]
                                    (init-tags-for-status-atm device device-map fetch-fn))
                                  pin-defs)))))
  (let [sample-pin-defs {:stepperX {:output-pins
                                    {17 {::gpio/tag :stepperX-ena
                                         :inverted? true}
                                     18 {::gpio/tag :stepperX-dir
                                         :inverted? true}
                                     19 {::gpio/tag :stepperX-pul
                                         :inverted? true}}
                                    :limit-switch-low  {:pin 4 :invert? true}
                                    :pos nil
                                    :pos-limit-inches 9}
                         :stepperZ {:output-pins
                                    {20 {::gpio/tag :stepperZ-ena
                                         :inverted? true}
                                     21 {::gpio/tag :stepperZ-dir
                                         :inverted? true}
                                     22 {::gpio/tag :stepperZ-pul
                                         :inverted? true}}
                                    :limit-switch-low  {:pin 6 :invert? false}
                                    :limit-switch-high {:pin 7 :invert? false}
                                    :pos nil
                                    :pos-limit-inches 4}}
        pin-defs-for-lib {:stepperX-limit-switch-low  false
                          :stepperZ-limit-switch-low  true
                          :stepperZ-limit-switch-high true}
        fetch-fn (fn [tag] true)]
    (is (= pin-defs-for-lib (init-status-atm nil sample-pin-defs fetch-fn)))))

(defn init-watcher [pin-defs]
  (let [gpio-chan (chan)
        gpio-multi-chan (mult gpio-chan) ; to support multiple subscribers to the events channel
        gpio-watcher (gpio/watcher
                      (:device @state-atom)
                      (get-input-pin-defs-for-gpio-lib pin-defs))
        last-event-timestamp (atom 0)
        status-atm (atom (init-status-atm gpio-watcher pin-defs))
        debounce-wait-time-ns (* 2 1000 1000)]
    ;; Add necessary atoms into the global state
    (swap! state-atom assoc :watcher gpio-watcher)
    (swap! state-atom assoc :status-atm status-atm)
    (println "Added status-atm to global state: " @status-atm)
    (thread (while (:watcher @state-atom) ; this gets closed in clean-up-pins
              (if-some [evt (gpio/event gpio-watcher 1000)]
                (do
                  (swap! last-event-timestamp
                         (fn [timestamp]
                           (let [tag (:dvlopt.linux.gpio/tag evt)
                                 evt-timestamp (:dvlopt.linux.gpio/nano-timestamp evt)
                                 transitioning-to-state (= :rising (:dvlopt.linux.gpio/edge evt))
                                 transitioning-to-state (if (is-inverted? pin-defs tag)
                                                          (not transitioning-to-state)
                                                          transitioning-to-state)
                                 ]
                             (if (and
                                  ; since the GPIO library sometimes generates multiple events of same direction (i.e. two :falling events), we need to check and only count an actual state transition as the signal before starting the debounce timer
                                  (not (= transitioning-to-state (get @status-atm tag)))
                                  (> evt-timestamp
                                     (+ timestamp debounce-wait-time-ns)))
                               (do
                                 (swap! status-atm (fn [s] (assoc s tag transitioning-to-state)))
                                 (println "Swapped " tag @status-atm)
                                 (put! gpio-chan evt)
                                 evt-timestamp)
                               timestamp))
                           ))))))))

(defn init-pins
  ([] (init-pins state-atom))
  ([state-atom]
   (swap! state-atom assoc :device (gpio/device 0))
   (swap! state-atom assoc :handle (gpio/handle (:device @state-atom)
                                                (get-output-pin-defs-for-gpio-lib (:setup @state-atom))
                                                {::gpio/direction :output}))
   (swap! state-atom assoc :buffer (gpio/buffer (:handle @state-atom)))
   (println "Calling init-watcher")
   (init-watcher pin-defs)
   ))

(defn clean-up-pins
  ([] (clean-up-pins state-atom))
  ([state-atom]
   (println "Cleaning up pins")
   (gpio/close (:handle @state-atom))
   (gpio/close (:device @state-atom))
   (gpio/close (:watcher @state-atom))
   (swap! state-atom dissoc :handle)
   (swap! state-atom dissoc :device)
   (swap! state-atom dissoc :buffer)
   (swap! state-atom dissoc :watcher)))
