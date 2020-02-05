(ns slide-stainer.defs
  (:require [dvlopt.linux.gpio :as gpio])
  (:use clojure.test))

(defn swap-in! [atom ks v]
  (swap-vals! atom #(assoc-in % ks v)))

(defonce state-atom (atom {}))

(def up-pos 2)
(def down-pos 0)
(def jar-positions
  {:jar-one 0
   :jar-two 2
   :jar-three 4})

(def pin-defs
  {:stepperZ {:output-pins
              {17 {::gpio/tag :stepperZ-ena
                   :inverted? false}
               18 {::gpio/tag :stepperZ-dir
                   :inverted? false}
               19 {::gpio/tag :stepperZ-pul
                   :inverted? false}}
              :limit-switch-low   {:pin 4 :invert? false}
              :limit-switch-high  {:pin 23 :invert? false}
              :travel_distance_per_turn 0.063
              :position-in-pulses 0
              :position_limit 9
              :pulses_per_revolution 800}
   :stepperX {:output-pins
              {26 {::gpio/tag :stepperX-ena
                   :inverted? false}
               27  {::gpio/tag :stepperX-dir
                   :inverted? false}
               21  {::gpio/tag :stepperX-pul
                    :inverted? false}}
              :travel_distance_per_turn 0.063
              :position-in-pulses 0
              :position_limit 12
              :pulses_per_revolution 800
              }
   :led13 {:pins
           {13 {::gpio/tag :led13-led}}}
   ;; :switch {:pins
   ;;          {4 {::gpio/tag :switch
   ;;              ::gpio/direction :input
   ;;              ::gpio/edge-detection :rising}}}
   })

(with-test
  (defn normalize-pin-tag [tag]
    "Take tag names and convert them to a keyword consistently."
    (keyword (clojure.string/replace tag #"^:" "")))
  (is (= (normalize-pin-tag ":foo") :foo))
  (is (= (normalize-pin-tag "foo")  :foo)))

(with-test
  (defn split-full-tag [full-tag]
    (let [tokens (clojure.string/split (str full-tag) #"-")
          device-tag (normalize-pin-tag (first tokens))
          pin-tag (keyword (clojure.string/join "-" (rest tokens)))]
      [device-tag pin-tag])
    )
  (is (= [:stepperZ :limit-switch-low] (split-full-tag :stepperZ-limit-switch-low))))

(with-test
  (defn is-inverted? [pin-defs full-tag]
    (let [split-tag (split-full-tag full-tag)]
      (get-in pin-defs [(first split-tag) (second split-tag) :invert?])))
  (let [sample-pin-defs   {:stepperZ {:output-pins
                                      {17 {::gpio/tag :stepperZ-ena
                                           :inverted? false}
                                       18 {::gpio/tag :stepperZ-dir
                                           :inverted? false}
                                       19 {::gpio/tag :stepperZ-pul
                                           :inverted? false}}
                                      :limit-switch-low   {:pin 4 :invert? false}
                                      :limit-switch-high  {:pin 4 :invert? true}}}]
    (is (= false (is-inverted? sample-pin-defs :stepperZ-limit-switch-low)))
    (is (= true (is-inverted? sample-pin-defs :stepperZ-limit-switch-high)))
    ))

(with-test
  (defn index-pin-defs [pin-defs]
    (apply merge
           (map (fn [[device {pins :output-pins}]]
                  (as-> pins $
                    (map (fn [[pin_number {tag :dvlopt.linux.gpio/tag inverted? :inverted?}]]
                           {tag {:inverted? inverted? :pin_number pin_number :device device}}) $)
                    (apply merge $))) pin-defs)))
  (let [sample-pin-defs {:stepperX {:output-pins
                                    {17 {::gpio/tag :stepperX-ena
                                         :inverted? true}
                                     18 {::gpio/tag :stepperX-dir
                                         :inverted? true}
                                     19 {::gpio/tag :stepperX-pul
                                         :inverted? true}}
                                    :pos nil
                                    :pos-limit-inches 9}
                         :stepperZ {:output-pins
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
  (defn get-output-pin-defs-for-gpio-lib [pin-defs]
    (apply merge (map (fn [[device {pins :output-pins}]]
                        (apply merge (map (fn [[pin_num {tag  ::gpio/tag
                                                         dir  ::gpio/direction
                                                         edge ::gpio/edge-detection}]]
                                            (let [pin-map (as-> {} $
                                                            (if tag (assoc $ ::gpio/tag tag)  $)
                                                            (if dir (assoc $ ::gpio/direction dir) $)
                                                            (if edge (assoc $ ::gpio/edge-detection edge) $))]
                                              {pin_num pin-map}))
                                          pins))) pin-defs)))
  (let [sample-pin-defs {:stepperX {:output-pins
                                    {17 {::gpio/tag :stepperX-ena
                                         :inverted? true}
                                     18 {::gpio/tag :stepperX-dir
                                         :inverted? true}
                                     19 {::gpio/tag :stepperX-pul
                                         :inverted? true}}
                                    :pos nil
                                    :pos-limit-inches 9}
                         :stepperZ {:output-pins
                                    {20 {::gpio/tag :stepperZ-ena
                                         :inverted? true}
                                     21 {::gpio/tag :stepperZ-dir
                                         :inverted? true}
                                     22 {::gpio/tag :stepperZ-pul
                                         :inverted? true}}
                                    :pos nil
                                    :pos-limit-inches 4}
                         :led12 {:output-pins
                                 {12 {::gpio/tag :led12-led
                                      :inverted? true}}}
                         :switch {:pins
                                  {4 {::gpio/tag :switch
                                      ::gpio/direction :input
                                      ::gpio/edge-detection :rising}}}}
        pin-defs-for-lib {12 {::gpio/tag :led12-led}
                          17 {::gpio/tag :stepperX-ena}
                          18 {::gpio/tag :stepperX-dir}
                          19 {::gpio/tag :stepperX-pul}
                          20 {::gpio/tag :stepperZ-ena}
                          21 {::gpio/tag :stepperZ-dir}
                          22 {::gpio/tag :stepperZ-pul}}]
    (is (= pin-defs-for-lib (get-output-pin-defs-for-gpio-lib sample-pin-defs)))))

(with-test
  (defn get-input-pin-defs-for-gpio-lib [pin-defs]
    (apply merge (map (fn [[device {limit-switch-low :limit-switch-low
                                    limit-switch-high :limit-switch-high}]]
                        (as-> {} $
                          (if limit-switch-low (assoc $ (:pin limit-switch-low) {:dvlopt.linux.gpio/tag (normalize-pin-tag (str device "-" "limit-switch-low"))
                                                                                 :dvlopt.linux.gpio/direction :input}) $)
                          (if limit-switch-high (assoc $ (:pin limit-switch-high) {:dvlopt.linux.gpio/tag (normalize-pin-tag (str device "-" "limit-switch-high"))
                                                                                 :dvlopt.linux.gpio/direction :input}) $))
                        ;; {(:pin limit-switch-low) {:dvlopt.linux.gpio/tag (normalize-pin-tag (str device "-" "limit-switch-low"))
                        ;;                           :dvlopt.linux.gpio/direction :input}
                        ;;  (:pin limit-switch-high) {:dvlopt.linux.gpio/tag (normalize-pin-tag (str device "-" "limit-switch-high"))
                        ;;                            :dvlopt.linux.gpio/direction :input}}
                        )
                      pin-defs)))
  (let [sample-pin-defs {:stepperX {:output-pins
                                    {17 {::gpio/tag :stepperX-ena
                                         :inverted? true}
                                     18 {::gpio/tag :stepperX-dir
                                         :inverted? true}
                                     19 {::gpio/tag :stepperX-pul
                                         :inverted? true}}
                                    :limit-switch-low  {:pin 4 :is-high-closed? true}
                                    :pos nil
                                    :pos-limit-inches 9}
                         :stepperZ {:output-pins
                                    {20 {::gpio/tag :stepperZ-ena
                                         :inverted? true}
                                     21 {::gpio/tag :stepperZ-dir
                                         :inverted? true}
                                     22 {::gpio/tag :stepperZ-pul
                                         :inverted? true}}
                                    :limit-switch-low  {:pin 6 :is-high-closed? false}
                                    :limit-switch-high {:pin 7 :is-high-closed? false}
                                    :pos nil
                                    :pos-limit-inches 4}}
        pin-defs-for-lib {4 {::gpio/tag :stepperX-limit-switch-low
                             ::gpio/direction :input}
                          6 {::gpio/tag :stepperZ-limit-switch-low
                             ::gpio/direction :input}
                          7 {::gpio/tag :stepperZ-limit-switch-high
                             ::gpio/direction :input}
                          }]
    (is (= pin-defs-for-lib (get-input-pin-defs-for-gpio-lib sample-pin-defs)))))

(with-test (defn init-tags-for-status-atm [device device-map fetch-fn]
             (let [tag-fn (fn [tag]
                            (let [full-tag  (normalize-pin-tag (str device "-" (clojure.string/replace tag #"^:" "")))
                                  fetched-val (fetch-fn full-tag)
                                  xformed-fetched-val (if (get-in device-map [tag :invert?])
                                                      (not fetched-val)
                                                      fetched-val)
                                  ]
                              {full-tag xformed-fetched-val}))]
               (apply merge (map tag-fn
                                 (clojure.set/intersection (set [:limit-switch-low :limit-switch-high])
                                                           (set (keys device-map)))))))
  (let [sample-device-map  { :limit-switch-low  {:pin 6 :invert? false}
                            :limit-switch-high {:pin 7 :invert? true}}
        fetch-fn (fn [tag] true)
        desired-output {:stepperZ-limit-switch-low  true
                        :stepperZ-limit-switch-high false}
        ]
    (is (= desired-output (init-tags-for-status-atm :stepperZ sample-device-map fetch-fn)))))

(defn inches-to-pulses [id inches]
  (println "foo " @state-atom)
  (let [axis-config (get-in @state-atom [:setup id])
        pulses-per-revolution (:pulses_per_revolution axis-config)
        travel-distance-per-turn (:travel_distance_per_turn axis-config)
        ]
    (int (/ (* inches pulses-per-revolution)
            travel-distance-per-turn))))

(defn pulses-to-inches [id pulses]
  (let [axis-config (get-in @state-atom [:setup id])
        pulses-per-revolution (:pulses_per_revolution axis-config)
        travel-distance-per-turn (:travel_distance_per_turn axis-config)
        ]
    (/ (* pulses travel-distance-per-turn)
       pulses-per-revolution)))
