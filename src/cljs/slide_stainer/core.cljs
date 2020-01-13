(ns slide-stainer.core
  (:require
   [reagent.core :as reagent]
   [cljs-http.client :as http]
   [clojure.edn :as edn]
   )
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Vars

(defonce app-state
  (reagent/atom {}))

(defn on-change-handler [atm evt]
  (reset! atm (-> evt .-target .-value)))

(defn graphql-control []
  (let [input  (reagent/atom
                "mutation {move_by_pulses(id:\":stepperX\",pulses:1000){id}}"
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
       [:button {:on-click (fn [e]
                             (go (let [resp (<! (http/post "http://localhost:3000/graphql"
                                                           {:json-params {:query @input}}
                                                           :with-credentials? false))]
                                   (reset! output-status (str resp))
                                   (reset! output-data   (:body resp))
                                   (println resp))))}
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
        [:tr [:th "ID"] [:th "Pin #"] [:th "Board Value"] [:th "Logical Value"]]
        (map (fn [pin]
               [:tr
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
             @pins)]
       [:button {:on-click update-fn} "Refresh"]])))


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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Page

(defn page [ratom]
  (let [screen (or (:screen @ratom) :main)]
    [:div
     [:div {:class "header"}
      [:button {:on-click #(swap! ratom (fn [v]  (assoc v :screen :main)))} "Main"]
      [:button {:on-click #(swap! ratom (fn [v]  (assoc v :screen :graphql)))} "GraphQL"]
      [:button {:on-click #(swap! ratom (fn [v]  (assoc v :screen :classic)))} "Classic"]]
     (when (= :graphql screen) [graphql-control])
     (when (= :main screen) [pins-control-graphql])
     (when (= :classic screen) [pins-control])
     ]))

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
