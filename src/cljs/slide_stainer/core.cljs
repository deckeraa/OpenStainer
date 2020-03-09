(ns slide-stainer.core
  (:require
   [reagent.core :as reagent]
   [devcards.core]
   [cljs-http.client :as http]
   [clojure.edn :as edn]
   [slide-stainer.atoms :as atoms]
   [slide-stainer.svg :as svg]
   [slide-stainer.graphql :as graphql]
   [slide-stainer.periodic-updater]
   [slide-stainer.settings]
   [slide-stainer.program-creation]
   [slide-stainer.procedure-selection]
   [slide-stainer.procedure-run])
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]
   [devcards.core :refer [defcard defcard-rg]]))

(defn on-change-handler [atm evt]
  (reset! atm (-> evt .-target .-value)))

(defn graphql-click-handler
  ([query]
   (graphql-click-handler query nil nil))
  ([query handler-fn]
   (graphql-click-handler query nil handler-fn))
  ([query query-fn handler-fn]
   (graphql-click-handler query query-fn handler-fn nil))
  ([query query-fn handler-fn variable-fn]
   (fn [e]
;     (println "graphql-click-handler variable-fn: " (variable-fn))
     (go (let [raw-resp (<! (http/post "http://localhost:8000/graphql"
                                       {:json-params {:query (or (if query-fn (query-fn) nil)
                                                                 query)
                                                      :variables (if variable-fn (variable-fn) nil)}}
                                       :with-credentials? false))
               resp (:data (edn/read-string (:body raw-resp)))]
           (println "resp: " resp)
           (println "raw-resp: " raw-resp)
           (println "handler-fn " handler-fn)
           (if handler-fn (handler-fn resp raw-resp)))))))

(def alarms-subquery "alarms{limit_switch_hit_unexpectedly,homing_failed}")
(def alarms-query (str "{state{" alarms-subquery "}}"))

(defn alarms-query-response-handler [alarms-cursor results]
  (println "alarms-query-response-handler " results)
  (reset! alarms-cursor (:alarms results)))

(defn alarms-control [alarms-cursor]
  (let [auto-loaded? (atom false)
        load-fn (graphql-click-handler
                 alarms-query
                 (fn [resp] (alarms-query-response-handler alarms-cursor (:state resp))))]
    (fn []
      (when (and (empty? @alarms-cursor) (not @auto-loaded?))
        (do
          (println "Need to auto-load here")
          (reset! auto-loaded? true))
        (load-fn))
      [:div
       (map (fn [[alarm val]]
              ^{:key alarm} [:div {:width 300 :style {:background-color (if val "red" "green")}} (str alarm)])
            @alarms-cursor)
       [:button {:on-click load-fn} "Refresh"]
       [:button {:on-click (graphql-click-handler
                            (str "mutation{clear_alarms{" alarms-subquery "}}")
                            (fn [resp] (alarms-query-response-handler
                                        alarms-cursor
                                        (:clear_alarms resp))))} "Clear alarms"]])))

(defcard-rg alarms-control-card
  (let [alarms-cursor (reagent/atom {:alarm-one true :alarm-two false})]
    (fn []
      [alarms-control alarms-cursor])))

(defn relative-jog-control []
  (let [query-fn (fn [device invert? inc-atm]
                   (str "mutation {move_relative(id:\""
                              device
                              "\",increment:"
                              (if invert? "-" "")
                              @inc-atm
                              "){id}}"))
        variable-fn (fn [device invert? inc-atm]
                      (str "{\"device\":\"" device
                           "\",\"increment\":" (if invert? (* -1 @inc-atm) @inc-atm)
                           "}"))
        increment (reagent/atom 1)]
    (fn []
      [:div
       [:input {:type "number" :value @increment
                :on-change (fn [evt] (do (println "change" (-> evt .-target .-value)) (reset! increment (-> evt .-target .-value))))}]
       [:table
        [:tbody
         [:tr
          [:td]
                                        ;(partial query-fn :stepperZ true increment)
                                        ;         [:td [:button {:on-click (graphql-click-handler "mutation{move_relative(id:$device,increment:$increment){id}}" nil nil (partial variable-fn :stepperZ true increment))} "Up"]]
          [:td [:button {:on-click (graphql-click-handler nil (partial query-fn :stepperZ false increment) nil)} "Up"]]
          [:td]]
         [:tr
          [:td [:button {:on-click (graphql-click-handler nil (partial query-fn :stepperX true increment) nil)} "Left"]]
          [:td]
          [:td [:button {:on-click (graphql-click-handler nil (partial query-fn :stepperX false increment) nil)} "Right"]]]
         [:tr
          [:td]
          [:td [:button {:on-click (graphql-click-handler nil (partial query-fn :stepperZ true increment) nil)} "Down"]]
          [:td]]]]])))

(defn position-readout-jog-control [device-id]
  (let [pul-atm (reagent/atom "")
        inch-atm (reagent/atom "")
        input-dirty-atm (reagent/atom false)
        axis-update-fn (fn [pul_atm inch_atm resp raw-resp]
                         (println "axis-update-fn" resp)
                         (let [body (or (:axis resp) (:set_axis resp))]
                           (println "axis-update-fn" body (:set_axis resp) (type resp))
                           (reset! pul_atm  (get-in body [:position]))
                           (reset! inch_atm (get-in body [:position_inches]))
                           (reset! input-dirty-atm false)))
        refresh-fn (graphql-click-handler
                    (str "{axis(id:\"" device-id "\"){position,position_inches}}") 
                    (partial axis-update-fn pul-atm inch-atm))]
    (fn []
      (when (= @pul-atm "") (refresh-fn))
      [:div
       [:h3 (str "Position for "  device-id)]
       [:div
        [:input {:value (or @pul-atm "")
                 :on-change (fn [e]
                              (reset! input-dirty-atm true)
                              (reset! pul-atm (-> e .-target .-value)))
                 :style {:color (if @input-dirty-atm :red :black)}}]
        "pulses"
        [:button {:on-click (graphql-click-handler
                            nil
                            (fn [] (str "mutation{set_axis(id:\"" device-id "\",position:" @pul-atm "){position,position_inches}}"))
                            (partial axis-update-fn pul-atm inch-atm))} "Set"]
        ]
       [:div
        [:input {:value (or @inch-atm "")
                 :on-change (fn [e]
                              (reset! input-dirty-atm true)
                              (reset! inch-atm (-> e .-target .-value)))
                 :style {:color (if @input-dirty-atm :red :black)}}]
        "inches"
        [:button {:on-click (graphql-click-handler
                            nil
                            (fn [] (str "mutation{set_axis(id:\"" device-id "\",position_inches:" @inch-atm "){position,position_inches}}"))
                            (partial axis-update-fn pul-atm inch-atm))} "Set"]]
       [:button {:on-click refresh-fn
                 } "Refresh"]])))

(defn absolute-jog-control [device]
  (let [dist-atm (reagent/atom 0)]
    (fn []
      [:div
       [:h3 (str device " position")]
       [:input {:value @dist-atm
                :on-change (fn [e] (reset! dist-atm (-> e .-target .-value)))}]
       [:button {:on-click (graphql-click-handler
                            nil
                            (fn [] (str "mutation{move_to_position(id:\"" device
                                        "\",position:" @dist-atm "){position}}"))
                            nil)
                 } "Move"]])))

(defn home-button []
  (fn []
    [:button {:on-click (graphql-click-handler "mutation {home{contents}}")}
     "Home"]))

(defn drop-motor-lock-button []
  [:button {:on-click (graphql/graphql-fn {:query "mutation{drop_motor_lock{motor_lock}}"})} "Drop motor lock"])

(defn jog-control [ratom]
  (let [alarms-cursor (reagent/cursor ratom [:alarms])]
    (fn []
      [:div
       [relative-jog-control]
       [position-readout-jog-control :stepperZ]
       [absolute-jog-control :stepperZ]
       [position-readout-jog-control :stepperX]
       [absolute-jog-control :stepperX]
;       [jar-jog-control alarms-cursor]
       [home-button]
       [alarms-control alarms-cursor]
       [drop-motor-lock-button]
       ])))

(defn replace-current-screen [screen-cursor new-screen]
  (swap! screen-cursor (fn [v] (conj (pop v) new-screen))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Page

(defn page [ratom]
  (fn []
    [:div
     [:div {:class "header"}
      [:h1 "OpenStain"]
      [:h2 "v1.0.0"]
      (when (> (count (filter true? (vals @atoms/alarms-cursor))) 0)
        [svg/bell {:class "bell" :on-click #(swap! atoms/screen-cursor conj :settings)} "white" 36])
      [svg/cog {:class "cog" :on-click #(swap! atoms/screen-cursor conj :settings)} "white" 36]]
     [:div {:class "body"}
      [:div {:class "button-bar"}
       ;; [:button {:on-click #(replace-current-screen atoms/screen-cursor :jog)} "Jog"]
       ;; [:button {:on-click #(replace-current-screen atoms/screen-cursor :procedure-selection)} "Procedure Selection"]
       ;; [:button {:on-click #(replace-current-screen atoms/screen-cursor :program-creation)} "Program Creation"]
       ;; [:button {:on-click #(replace-current-screen atoms/screen-cursor :procedure-run)} "Procedure Run Status"]
       ]
      (when (= :jog (peek @atoms/screen-cursor)) [jog-control ratom])
      (when (= :procedure-selection (peek @atoms/screen-cursor))
        [slide-stainer.procedure-selection/procedure-selection atoms/procedure-list-cursor atoms/procedure-cursor
         #(swap! atoms/screen-cursor conj :program-creation)
         ])
      (when (= :program-creation (peek @atoms/screen-cursor))
        [slide-stainer.program-creation/program-creation
         atoms/procedure-cursor
         atoms/procedure-run-status-cursor
         #(swap! atoms/screen-cursor pop)
         (fn [procedure]
           (println "Running run-fn")
           (swap! ratom (fn [v] (-> v
                                    (assoc :current-procedure procedure)
                                    )))
           (swap! atoms/screen-cursor conj :procedure-run)
           (println "@screen-cursor is now: " @atoms/screen-cursor)
                                        ;              (swap! procedure-cursor (fn [v] (assoc v :current_procedure_step_number 1)))
           )])
      (when (= :procedure-run (peek @atoms/screen-cursor))
        [slide-stainer.procedure-run/procedure-run-status atoms/procedure-cursor
         atoms/procedure-run-status-cursor
         #(swap! atoms/screen-cursor pop) ;; TODO make this stop the currently running staining procedure (maybe?)
         ])
      (when (= :settings (peek @atoms/screen-cursor)) [slide-stainer.settings/settings-control ratom #(swap! atoms/screen-cursor pop)])
      [:div {} (str @ratom)]]
     ]))

(def queries-to-run
  {:init {:query-fn (fn [] (str "{settings{developer}},{procedures{_id,name,runs}}"))
          :handler-fn (fn [resp]
                        (reset! atoms/settings-cursor (:settings resp))
                        (reset! atoms/procedure-list-cursor (:procedures resp)))
          :should-run? (fn [] (empty? @atoms/settings-cursor))}
   ;; :always {:query-fn (fn [] (str "{state{alarms{" graphql/alarm-keys "}}}"))
   ;;          :handler-fn (fn [resp] (reset! atoms/alarms-cursor (get-in resp [:state :alarms])))}
   :procedure-run {:query-fn slide-stainer.procedure-run/refresh-query-fn
                   :handler-fn (partial slide-stainer.procedure-run/refresh-handler-fn atoms/procedure-cursor atoms/procedure-run-status-cursor)}
   :settings {:query-fn slide-stainer.settings/refresh-query-fn :handler-fn slide-stainer.settings/refresh-handler-fn}})

;; start the updater
(defonce periodic-updater-instance
  (js/setTimeout (fn [] (slide-stainer.periodic-updater/periodic-updater
                         atoms/screen-cursor queries-to-run))
                 (* 1 1000)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Initialize App

(defn dev-setup []
  (when ^boolean js/goog.DEBUG
    (enable-console-print!)
    (println "dev mode")
    ))

(defn reload []
  (reagent/render [page atoms/app-state]
                  (.getElementById js/document "app")))

(defn ^:export main []
  (dev-setup)
  (reload))
