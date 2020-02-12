(ns slide-stainer.core
  (:require
   [reagent.core :as reagent]
   [devcards.core]
   [cljs-http.client :as http]
   [clojure.edn :as edn]
   [slide-stainer.program-creation]
   [slide-stainer.procedure-selection]
   [slide-stainer.procedure-run])
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]
   [devcards.core :refer [defcard defcard-rg]]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Vars

(defonce app-state
  (reagent/atom {:alarms {}
                 :current-procedure nil}))

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

(def alarms-subquery "alarms{limit_switch_hit_unexpectedly}")
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


(defn pin-control [pin-tag]
  (let [style {:background-color :red
               :color :white
               :font-size "40px"
               :margin "5px"
               :width "75px"
               :height "75px"}]
    [:tr
     [:td (str pin-tag)]
     [:td
      [:div {:style style
             :on-click (fn [e]
                         (go (let [resp (http/post "http://localhost:3000/pin"
                                                   {:json-params {:pin-tag pin-tag :state true}}
                                                   :with-credentials? false)]
                               (println "POST Resp: " resp))))
             } "Off"]]
     [:td
      [:div {:style (assoc style :background-color :green)
             :on-click (fn [e]
                         (go (let [resp (http/post "http://localhost:3000/pin"
                                                   {:json-params {:pin-tag pin-tag :state false}}
                                                   :with-credentials? false)]
                               (println "POST Resp: " resp))))
             } "On"]]]))

(defn pulse-control [pin-tag]
  (let [wait-ms    (reagent/atom "100")
        num-pulses (reagent/atom "40000")]
    (fn []
      [:tr (str pin-tag)
       [:td [:input {:type "button" :value "Pulse"
                     :style {:height 100}
                     :on-click (fn [e]
                                 (go (let [resp (http/post "http://localhost:3000/pulse"
                                                           {:json-params {:pin-tag pin-tag :wait-ms @wait-ms :num-pulses @num-pulses}}
                                                           :with-credentials? false)]
                                       (println "POST resp: " resp))))}]]
       [:td [:input {:type "text" :value @wait-ms
                     :on-change #(reset! wait-ms (-> % .-target .-value))}]]
       [:td [:input {:type "text" :value @num-pulses
                     :on-change #(reset! num-pulses (-> % .-target .-value))}]]])))

(defn pins-control []
  (into
   [:table [pulse-control :stepperX-pul]]
   (map pin-control [:stepperX-ena :stepperX-dir]))
  )

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
       ])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Page

(defn page [ratom]
  (let [procedure-cursor (reagent/cursor ratom [:current-procedure])]
    (fn []
      (let [screen (or (:screen @ratom) :main)]
        [:div
         [:div {:class "header"}
          [:button {:on-click #(swap! ratom (fn [v]  (assoc v :screen :main)))} "Main"]
          [:button {:on-click #(swap! ratom (fn [v]  (assoc v :screen :graphql)))} "GraphQL"]
          [:button {:on-click #(swap! ratom (fn [v]  (assoc v :screen :classic)))} "Classic"]
          [:button {:on-click #(swap! ratom (fn [v]  (assoc v :screen :jog)))} "Jog"]
          [:button {:on-click #(swap! ratom (fn [v]  (assoc v :screen :program-creation)))} "Program Creation"]
          [:button {:on-click #(swap! ratom (fn [v]  (assoc v :screen :procedure-selection)))} "Procedure Selection"]
          [:button {:on-click #(swap! ratom (fn [v]  (assoc v :screen :procedure-run)))} "Procedure Run Status"]]
         (when (= :graphql screen) [graphql-control])
         (when (= :main screen) [pins-control-graphql])
         (when (= :classic screen) [pins-control])
         (when (= :jog screen) [jog-control ratom])
         (when (= :program-creation screen) [slide-stainer.program-creation/program-creation procedure-cursor])
         (when (= :procedure-selection screen) [slide-stainer.procedure-selection/procedure-selection procedure-cursor (fn [] (swap! ratom (fn [v] (assoc v :screen :program-creation))))])
         (when (= :procedure-run screen) [slide-stainer.procedure-run/procedure-run-status])
         ]))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Initialize App

(defn dev-setup []
  (when ^boolean js/goog.DEBUG
    (enable-console-print!)
    (println "dev mode")
    ))

(defn reload []
  (reagent/render [page app-state]
                  (.getElementById js/document "app")))

(defn ^:export main []
  (dev-setup)
  (reload))
