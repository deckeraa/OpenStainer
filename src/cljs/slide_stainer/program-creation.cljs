(ns slide-stainer.program-creation
  (:require [reagent.core :as reagent]
            [cljs-http.client :as http]
            [devcards.core :refer-macros [deftest defcard-rg]]
            [cljs.test :refer-macros [is testing run-tests]]
            [clojure.edn :as edn]
            [slide-stainer.graphql :as graphql]
            [slide-stainer.onscreen-keyboard :as osk])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(def number-of-jars 6)

(def sample-program
  {:name "H&E with Harris' Hematoxylin"
   :type :procedure
   :jar_contents ["Hematoxylin" "Tap water" "70% ethanol/1% HCI" "Tap water" "Eosin"]
   :procedure_steps
   [{:substance "Hematoxylin" :time_in_seconds (* 25 60) :jar_number 1}
    {:substance "Tap water" :time_in_seconds 150 :jar_number 2}]})

(def sample-program-atom (reagent/atom sample-program))
(def osk-atm (reagent/atom {}))

(defn convert-keywords-to-strings [form]
  (clojure.walk/postwalk (fn [x]  (if (keyword? x)
                                    (name x)
                                    x)) sample-program))

(defn remove-quotes-from-keys
  "This removes quotes from the keyword in a JSON string to make it compatible with GraphQL."
  [s]
  (clojure.string/replace s #"\"(\w+)\":" "$1:"))

(deftest remove-quotes-from-keys-test
  (is (= (remove-quotes-from-keys "{\"name\":\"foo\"}") "{name:\"foo\"}")))

(defn jsonify [s]
  (.stringify js/JSON (clj->js s)))

(defn rename-substance [prog-atm jar_number new-substance]
  "Don't forget that jar_number is 1-indexed."
  (swap! prog-atm (fn [prog]
                    (as-> prog $
                      (assoc-in $ [:jar_contents (dec jar_number)] new-substance)
                      (assoc-in $ [:procedure_steps] (mapv (fn [step]
                                                             (if (= jar_number (:jar_number step))
                                                               (assoc step :substance new-substance)
                                                               step))
                                                           (:procedure_steps $)))
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
                         (extend-vector (:jar_contents @prog-atm) number-of-jars))]]])

(defn substance-selector [option-list step-cursor]
  "Ex: (substance-selector [\"Hematoxylin\" \"Tap water\" \"Eosin\"] \"Eosin\")"
  (fn [option-list step-cursor]
    (let [substance (:substance @step-cursor)]
      [:select {:name "substance" :value (or substance "")
                :on-change (fn [e]
                             (let [new-substance (-> e .-target .-value)]
                               (swap! step-cursor
                                      (fn [substance]
                                        (as-> substance $
                                          (assoc $ :substance new-substance)
                                          (assoc $ :jar_number (inc (.indexOf option-list new-substance))))))))}
       (map-indexed (fn [idx option] ^{:key idx} [:option {:value option} option])
                    (if (empty? substance) (cons "" option-list) option-list))])))

(defn jar-selector [option-list step-cursor]
  (fn [option-list step-cursor]
    (let [options (as-> option-list $
                    (map-indexed (fn [idx itm] [(inc idx) itm]) $)
                    (filter #(= (:substance @step-cursor) (second %)) $)
                    (mapv first $))]
      (if (> (count options) 1)
        [:select {:name "jar_number" :value (or (:jar_number @step-cursor) "")
                  :on-change (fn [e] (swap! step-cursor #(assoc % :jar_number (js/parseInt (-> e .-target .-value)))))}
         (map-indexed (fn [idx option] ^{:key idx} [:option {:value option} option]) options)]
        [:div (:jar_number @step-cursor)]))))

(defn render-time [time_in_seconds]
  (let [minutes (Math/floor (/ time_in_seconds 60))
        seconds (str (rem time_in_seconds 60))
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
  (let [time_in_seconds (:time_in_seconds @step-cursor)
        minutes (Math/floor (/  time_in_seconds 60))
        minutes-atm (reagent/atom (str minutes))
        seconds (str (rem time_in_seconds 60))
        padded-seconds (if (= 1 (count seconds)) (str 0 seconds) seconds)
        seconds-atm (reagent/atom padded-seconds)
        update-seconds (fn []
                         (swap! step-cursor assoc :time_in_seconds (+ (* 60 (first (parse-and-pad @minutes-atm)))
                                                                      (first (parse-and-pad @seconds-atm)))))]
    (fn [step-cursor]
      [:div
       [osk/osk-input osk-atm
        {:type "text" :value @minutes-atm
         :size 2
         :on-change (fn [new-minutes] 
                      (println new-minutes (type new-minutes))
                      (when (re-matches #"[0-9]*" new-minutes)
                        (reset! minutes-atm new-minutes)))
         :on-blur (fn [_] (update-seconds))}]
       ":"
       [osk/osk-input osk-atm
        {:type "text" :value @seconds-atm
                :size 2
         :on-change (fn [new-seconds]
                      (when (re-matches #"^[0-9]{0,2}$" new-seconds)
                        (reset! seconds-atm new-seconds)))
         :on-blur (fn [_]
                    (let [parsed-seconds (parse-and-pad @seconds-atm)]
                      (reset! seconds-atm (second parsed-seconds))
                      (update-seconds)))}]
       ])))

(defn up-down-field [label atm]
  [:div
   [:div {:style {:display "inline-block"}} (str label @atm)]
   [:button {:on-click (fn [e] (swap! atm (fn [x] (dec (or x 0)))))} "-"]
   [:button {:on-click (fn [e] (swap! atm (fn [x] (inc (or x 0)))))} "+"]])

(defn drop-nth [coll n]
  (as-> coll $
    (map-indexed vector $)
    (remove (fn [[idx itm]] (= n idx)) $)
    (mapv (fn [[idx itm]] itm) $)))

(defn procedure-steps [prog-atm run-fn]
  (fn []
    (let [steps-cursor (reagent/cursor prog-atm [:procedure_steps])
          repeat-cursor (reagent/cursor prog-atm [:repeat])
          substance-options (:jar_contents @prog-atm)
          save-query (str "mutation{save_procedure(procedure:"
                     (-> @prog-atm
                         (jsonify)
                         (remove-quotes-from-keys))
                     "){" graphql/procedure-keys "}}")]
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
       [:button {:on-click (fn [e] (swap! steps-cursor conj {}))} "+"]
       [up-down-field "Repeat: " repeat-cursor]
       
       [:button {:on-click (slide-stainer.graphql/graphql-fn
                            {:query save-query
                             :handler-fn (fn [resp]
                                           (println "Save button's response" resp)
                                           (when resp (reset! prog-atm (:save_procedure resp))))})} "Save"]
       [:button {:on-click (fn [e]
                             ;; ((slide-stainer.graphql/graphql-fn
                             ;;          {:query (str "mutation{run_procedure(_id:\"" (:_id @prog-atm) "\"){contents}}")}))
                             (when run-fn (run-fn @prog-atm)))
                 } "Run"]
;       [:p save-query]
       ])))

(defn program-creation
  ([] (program-creation sample-program-atom nil))
  ([prog-atm run-fn]
   [:div
    [osk/osk-input osk-atm
                                  {:on-change (fn [new-val]
                                                (println "Change handler called: " new-val)
                                                (swap! prog-atm (fn [x] (assoc x :name new-val))))
                                   :value (:name @prog-atm)
                                   :size 40}]
    [jar-contents prog-atm]
    [procedure-steps prog-atm run-fn]
    [:div (str @prog-atm)]
    [:div (str @osk-atm)]
    [slide-stainer.onscreen-keyboard/onscreen-keyboard osk-atm]]))

(defcard-rg procedure-edit-card
  [program-creation])
