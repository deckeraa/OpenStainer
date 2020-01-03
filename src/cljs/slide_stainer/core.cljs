(ns slide-stainer.core
  (:require
   [reagent.core :as reagent]
   [cljs-http.client :as http]
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
  (let [input  (reagent/atom "{pin_by_id(id:123){board_value},ip{inet4}}")
        output-data (reagent/atom "")
        output-status (reagent/atom "")]
    (fn []
      [:div
       [:h1 "GraphQL Control"]
       [:textarea {:type "text" :value @input
                   :rows 8 :cols 40
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
                     (go (let [resp (http/post "http://localhost:3000/blink"
                                               {:json-params {:port num}}
                                               :with-credentials? false)]
                           (println "POST Resp: " resp))))
         } name])


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
        num-pulses (reagent/atom "400")]
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
   [:table [pulse-control :pul]]
   (map pin-control [:ena :dir]))
  )

;; (defn ip-control []
;;   (let [ip (reagent/atom nil)]
;;     (fn []
;;       )))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Page

(defn page [ratom]
  [:div
   [graphql-control]
   [pins-control]
;   [led-button "LED" 17]
;   [led-button 18 18]
   ])

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
