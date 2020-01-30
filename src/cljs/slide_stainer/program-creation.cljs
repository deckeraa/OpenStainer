(ns slide-stainer.program-creation
  (:require [reagent.core :as reagent]
            [cljs-http.client :as http]
            [cljs.test :refer-macros [deftest is testing run-tests]]
            [slide-stainer.onscreen-keyboard :as osk])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(def number-of-jars 6)

(def sample-program
  {:name "H&E with Harris' Hematoxylin"
   :jar-contents ["Hematoxylin" "Tap water" "70% ethanol/1% HCI" "Tap water" "Eosin"]
   :procedure-steps
   [{:substance "Hematoxylin" :time-in-seconds (* 25 60) :jar-number 1}
    {:substance "Tap water" :time-in-seconds 150 :jar-number 2}]})

(def sample-program-atom (reagent/atom sample-program))
(def osk-atm (reagent/atom {}))

(defn rename-substance [prog-atm jar-number new-substance]
  "Don't forget that jar-number is 1-indexed."
  (swap! prog-atm (fn [prog]
                    (as-> prog $
                      (assoc-in $ [:jar-contents (dec jar-number)] new-substance)
                      (assoc-in $ [:procedure-steps] (mapv (fn [step]
                                                             (if (= jar-number (:jar-number step))
                                                               (assoc step :substance new-substance)
                                                               step))
                                                           (:procedure-steps $)))
                      ))))

(defn extend-vector [coll n]
  (vec (concat coll (take (- n (count coll)) (repeat "")))))

(defn jar-contents [prog-atm]
  [:div
   [:h3 "Jar Contents"]
   [:table [:tbody [:tr [:th "Jar #"] [:th "Substance"]]
            (map-indexed (fn [idx substance]
                           ^{:key idx}
                           [:tr
                            [:td (inc idx)]
                            [:td [osk/osk-input osk-atm
                                  {:on-change (fn [new-val]
                                                (println "Change handler called: " new-val)
                                                (rename-substance prog-atm (inc idx) new-val))
                                   :value substance}]]]
                                  ;; [:input {:type "text" :value substance
                             ;;          :on-change (fn [e] (rename-substance prog-atm (inc idx) (-> e .-target .-value)))}]
                           )
                         (extend-vector (:jar-contents @prog-atm) number-of-jars))]]])

(defn substance-selector [option-list step-cursor]
  "Ex: (substance-selector [\"Hematoxylin\" \"Tap water\" \"Eosin\"] \"Eosin\")"
  (fn [option-list step-cursor]
    (let [substance (:substance @step-cursor)]
      [:select {:name "substance" :value substance
                :on-change (fn [e]
                             (let [new-substance (-> e .-target .-value)]
                               (swap! step-cursor
                                      (fn [substance]
                                        (as-> substance $
                                          (assoc $ :substance new-substance)
                                          (assoc $ :jar-number (inc (.indexOf option-list new-substance))))))))}
       (map-indexed (fn [idx option] ^{:key idx} [:option {:value option} option])
                    (if (empty? substance) (cons "" option-list) option-list))])))

(defn jar-selector [option-list step-cursor]
  (fn [option-list step-cursor]
    (let [options (as-> option-list $
                    (map-indexed (fn [idx itm] [(inc idx) itm]) $)
                    (filter #(= (:substance @step-cursor) (second %)) $)
                    (mapv first $))]
      (if (> (count options) 1)
        [:select {:name "jar-number" :value (:jar-number @step-cursor)
                  :on-change (fn [e] (swap! step-cursor #(assoc % :jar-number (-> e .-target .-value))))}
         (map-indexed (fn [idx option] ^{:key idx} [:option {:value option} option]) options)]
        [:div (:jar-number @step-cursor)]))))

(defn render-time [time-in-seconds]
  (let [minutes (Math/floor (/ time-in-seconds 60))
        seconds (str (rem time-in-seconds 60))
        padded-seconds (if (= 1 (count seconds)) (str 0 seconds) seconds)]
    (str minutes ":" padded-seconds)))

(defn- parse-and-pad [time]
  "Parses a time and pads out the time to two digits"
  (let [time (if (= time "") "0" time) ; blank means zero in this case
        parsed-time (js/parseInt time)]
    (cond (not (integer? parsed-time)) [nil nil]
          (= 2 (count time)) [parsed-time time]
          (= 1 (count time)) [parsed-time (str "0" time)])))

(deftest test-parse-and-pad
  (is (= [nil nil] (parse-and-pad "abc")))
  (is (= [15 "15"] (parse-and-pad "15")))
  (is (= [1 "01"] (parse-and-pad "1")))
  (is (= [0 "00"] (parse-and-pad ""))))

(defn time-display [step-cursor]
  (let [time-in-seconds (:time-in-seconds @step-cursor)
        minutes (Math/floor (/  time-in-seconds 60))
        minutes-atm (reagent/atom (str minutes))
        seconds (str (rem time-in-seconds 60))
        padded-seconds (if (= 1 (count seconds)) (str 0 seconds) seconds)
        seconds-atm (reagent/atom padded-seconds)
        update-seconds (fn []
                         (swap! step-cursor assoc :time-in-seconds (+ (* 60 (first (parse-and-pad @minutes-atm)))
                                                                      (first (parse-and-pad @seconds-atm)))))]
    (fn [step-cursor]
      [:div
       [:input {:type "text" :value @minutes-atm
                :size 2
                :on-change (fn [e] (let [new-minutes (-> e .-target .-value)]
                                     (println new-minutes (type new-minutes))
                                     (when (re-matches #"[0-9]*" new-minutes)
                                       (reset! minutes-atm new-minutes))))
                :on-blur (fn [e] (update-seconds))}]
       ":"
       [:input {:type "text" :value @seconds-atm
                :size 2
                :on-change (fn [e] (let [new-seconds (-> e .-target .-value)]
                                     (when (re-matches #"^[0-9]{0,2}$" new-seconds)
                                       (reset! seconds-atm new-seconds))))
                :on-blur (fn [e]
                           (let [parsed-seconds (parse-and-pad @seconds-atm)]
                             (reset! seconds-atm (second parsed-seconds))
                             (update-seconds)))}]])))

(defn drop-nth [coll n]
  (as-> coll $
    (map-indexed vector $)
    (remove (fn [[idx itm]] (= n idx)) $)
    (mapv (fn [[idx itm]] itm) $)))

(defn procedure-steps [prog-atm]
  (fn []
    (let [steps-cursor (reagent/cursor prog-atm [:procedure-steps])
          substance-options (:jar-contents @prog-atm)]
                                        ;      (println "Re-running procedure-steps: " substance-options)
      [:div
       
       [:h3 "Procedure Steps"]
       [:table
        [:tbody [:tr [:th "Step #"] [:th "Substance"] [:th "Time"] [:th "Jar #"]]
         (map-indexed (fn [idx step]
                        (let [step-cursor (reagent/cursor steps-cursor [idx])]
                          ^{:key idx}
                          [:tr
                           [:td (inc idx)]
                           [:td [substance-selector substance-options step-cursor]]
                           [:td [time-display step-cursor]]
                           [:td [jar-selector substance-options step-cursor]]
                           [:td [:button {:on-click (fn [e] (swap! steps-cursor
                                                                   (fn [steps]
                                                                     (println steps)
                                                                     (println idx)
                                                                     (println (drop-nth steps idx))
                                                                     (drop-nth steps idx))))} "x"]]]))
                      @steps-cursor)]]
       [:button {:on-click (fn [e] (swap! steps-cursor conj {}))} "+"]])))

(defn program-creation
  ([] (program-creation sample-program-atom))
  ([prog-atm]
   [:div
    [:h2 (:name @prog-atm)]
    [jar-contents prog-atm]
    [procedure-steps prog-atm]
    [:div (str @prog-atm)]
    [:div (str @osk-atm)]
    [slide-stainer.onscreen-keyboard/onscreen-keyboard osk-atm]]))
