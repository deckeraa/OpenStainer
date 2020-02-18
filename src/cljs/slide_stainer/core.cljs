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
     (go (let [raw-resp (<! (http/post "http://localhost:3000/graphql"
                                       {:json-params {:query (or (if query-fn (query-fn) nil)
                                                                 query)
                                                      :variables (if variable-fn (variable-fn) nil)}}
                                       :with-credentials? false))
               resp (:data (edn/read-string (:body raw-resp)))]
           (println "resp: " resp)
           (println "raw-resp: " raw-resp)
           (println "handler-fn " handler-fn)
           (if handler-fn (handler-fn resp raw-resp)))))))

(defn graphql-control []
  (let [input  (reagent/atom
                "{state{procedure_run_status{current_procedure_id,current_procedure_name,current_procedure_step_number,current_procedure_step_start_time}}}"
;                "mutation{run_procedure(name:\"foo\"){contents}}"
;                "mutation{move_to_position(id:\":stepperZ\",position:2.2){id,position_inches}}"
                ;"mutation {move_by_pulses(id:\":stepperX\",pulses:1000){id}}"
                ;"{pin_by_id(id:\":stepperX-ena\"){board_value,logical_value,pin_number},ip{inet4}}"
                )
        output-data (reagent/atom "")
        output-status (reagent/atom "")]
    (fn []
      [:div
       [:h1 "GraphQL Control"]
       [:textarea {:type "text" :value @input
                   :rows 8 :cols 80
                :on-change #(on-change-handler input %)
                }]
       [:textarea {:type "text" :value @output-data
                   :rows 12 :cols 40
                   :on-change #(on-change-handler output-data %)
                   }]
       [:textarea {:type "text" :value @output-status
                   :rows 4 :cols 40
                   :on-change #(on-change-handler output-data %)
                }]
       [:button {:on-click (graphql-click-handler nil
                                                  (fn [] (deref input))
                                                  (fn [resp raw-resp]
                                                    (reset! output-status (str raw-resp))
                                                    (reset! output-data (str resp))))
                 ;; (fn [e]
                           ;;   (go (let [resp (<! (http/post "http://localhost:3000/graphql"
                           ;;                                 {:json-params {:query @input}}
                           ;;                                 :with-credentials? false))]
                           ;;         (reset! output-status (str resp))
                           ;;         (reset! output-data   (:body resp))
                           ;;         (println "body type" (type (:body resp)))
                           ;;         (println resp))))
                 }
        "Run query"]])))

(defn led-button [name num]
  [:div {:style {:background-color :red
                 :color :white
                 :font-size "40px"
                 :margin "5px"
                 :width "100px"
                 :height "100px"}
         :on-click (fn [e]
                     (console.log "Click!")
                     (go (let [resp (<! (http/post "http://localhost:3000/blink"
                                                   {:json-params {:port num}}
                                                   :with-credentials? false))]
                           (println "POST Resp: " resp))))
         } name])

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

(defn pins-control-graphql []
  (let [pins (reagent/atom [])
        update-from-resp (fn [raw-resp]
                           (reset! pins (:pins (:data (edn/read-string (:body raw-resp)))))
                           )
        update-fn (fn []
                    (go (let [resp (<! (http/post "http://localhost:3000/graphql"
                                                  {:json-params {:query "{pins{id,pin_number,board_value,logical_value}}"}}
                                                  :with-credentials? false))]
                          (println "pins-control-graphql" resp)
;                          (println "line 2" (:data (edn/read-string (:body resp))))
                                        ;                          (reset! pins (:pins (:data (edn/read-string (:body resp)))))
                          (update-from-resp resp)
                          ))
                    )]
    (fn []
      (when (empty? @pins) (update-fn))
      [:div
       [:p (str @pins)]
       [:table
        [:tbody
         [:tr [:th "ID"] [:th "Pin #"] [:th "Board Value"] [:th "Logical Value"]]
         (map (fn [pin]
                ^{:key pin} [:tr
                             [:td (:id pin)]
                             [:td (:pin_number pin)]
                             [:td (str (:board_value pin))]
                             (let [val (:logical_value pin)]
                               [:td [:button {:style {:height 75 :width 75}
                                              :on-click (fn [e]
                                                          (go (let [resp (<! (http/post "http://localhost:3000/graphql" {:json-params {:query (str "mutation {set_pin(id:\"" (:id pin) "\",logical_value:" (not val) "){id,pin_number,board_value,logical_value}}")}} :with-credentials? false))]
                                                                (println "mutate RESP" (str resp))
                                                                ))
                                                          )}
                                     (str val)]])
                             ])
              @pins)]]
       [:button {:on-click update-fn} "Refresh"]
       [:p]
       [:button {:on-click (graphql-click-handler "mutation {clean_up_pins{contents}}")} "Clean up pins"]])))

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

(defn jar-jog-control [alarms-cursor]
  (let [query-fn (fn [jar] (str "mutation{move_to_jar(jar:" jar "){position," alarms-subquery "}}"))]
    (fn []
      [:div
       (map (fn [jar]
              ^{:key jar} [:button {:on-click (graphql-click-handler
                                               nil
                                               (partial query-fn jar)
                                               (fn [resp]
                                                 (println "jar-jog-control response handler" resp)
                                                 (alarms-query-response-handler alarms-cursor (:move_to_jar resp))))}
            (str "Jar #" jar)])
            (range 1 7))
       ])))

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
       [jar-jog-control alarms-cursor]
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
       [:button {:on-click #(replace-current-screen atoms/screen-cursor :main)} "Main"]
       [:button {:on-click #(replace-current-screen atoms/screen-cursor :graphql)} "GraphQL"]
       [:button {:on-click #(replace-current-screen atoms/screen-cursor :jog)} "Jog"]
       [:button {:on-click #(replace-current-screen atoms/screen-cursor :procedure-selection)} "Procedure Selection"]
       [:button {:on-click #(replace-current-screen atoms/screen-cursor :program-creation)} "Program Creation"]
       [:button {:on-click #(replace-current-screen atoms/screen-cursor :procedure-run)} "Procedure Run Status"]]
      (when (= :graphql (peek @atoms/screen-cursor)) [graphql-control])
      (when (= :main (peek @atoms/screen-cursor)) [pins-control-graphql])
      (when (= :jog (peek @atoms/screen-cursor)) [jog-control ratom])
      (when (= :procedure-selection (peek @atoms/screen-cursor))
        [slide-stainer.procedure-selection/procedure-selection atoms/procedure-list-cursor atoms/procedure-cursor
         #(replace-current-screen atoms/screen-cursor :program-creation)
         ])
      (when (= :program-creation (peek @atoms/screen-cursor))
        [slide-stainer.program-creation/program-creation
         atoms/procedure-cursor
         atoms/procedure-run-status-cursor
         (fn [procedure]
           (println "Running run-fn")
           (swap! ratom (fn [v] (-> v
                                    (assoc :current-procedure procedure)
                                    )))
           (swap! atoms/screen-cursor conj :procedure-run)
           (println "@screen-cursor is now: " @atoms/screen-cursor)
                                        ;              (swap! procedure-cursor (fn [v] (assoc v :current_procedure_step_number 1)))
           )])
      (when (= :procedure-run (peek @atoms/screen-cursor)) [slide-stainer.procedure-run/procedure-run-status atoms/procedure-cursor atoms/procedure-run-status-cursor])
      (when (= :settings (peek @atoms/screen-cursor)) [slide-stainer.settings/settings-control ratom #(swap! atoms/screen-cursor pop)])
      [:div {} (str @ratom)]]
     ]))

(def queries-to-run
  {:always {:query-fn (graphql/graphql-fn {:query (str "{state{alarms{" graphql/alarm-keys "}}}")
                                           :handler-fn (fn [resp] (reset! atoms/alarms-cursor (get-in resp [:state :alarms])))})}
   :procedure-run {:query-fn (slide-stainer.procedure-run/refresh-fn atoms/procedure-cursor atoms/procedure-run-status-cursor)}
   :settings {:query-fn (slide-stainer.settings/refresh-fn)}})

;; start the updater
(defonce periodic-updater-instance
  (js/setTimeout (fn [] (slide-stainer.periodic-updater/periodic-updater
                         atoms/screen-cursor queries-to-run))
                 (* 5 1000)))

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
