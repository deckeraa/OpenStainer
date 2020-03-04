(ns slide-stainer.settings
  (:require
   [reagent.core :as reagent]
   [devcards.core]
   [cljs-http.client :as http]
   [clojure.edn :as edn]
   [slide-stainer.atoms :as atoms]
   [slide-stainer.svg :as svg]
   [slide-stainer.graphql :as graphql])
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]
   [devcards.core :refer [defcard defcard-rg]]))

(defn refresh-fn []
  (graphql/graphql-fn {:query (str "{stepperX: axis(id:\"stepperX\"){position_inches}, stepperZ: axis(id:\"stepperZ\"){position_inches}}")
                       :handler-fn (fn [resp]
                                     (swap! atoms/stepperX-cursor (fn [v] (merge v (:stepperX resp))))
                                     (swap! atoms/stepperZ-cursor (fn [v] (merge v (:stepperZ resp)))))}))

(defn refresh-query-fn []
  (str "{stepperX: axis(id:\"stepperX\"){position_inches}, stepperZ: axis(id:\"stepperZ\"){position_inches}}")
  )

(defn refresh-handler-fn [resp]
  (swap! atoms/stepperX-cursor (fn [v] (merge v (:stepperX resp))))
  (swap! atoms/stepperZ-cursor (fn [v] (merge v (:stepperZ resp))))
  )

(defn alarm-line [alarmed? label]
  (fn []
    [:div {:style {:display :flex :flex-direction :row :align-items :center}}
     [svg/bell {:style {:margin "5px"}} (if alarmed? "red" "green") 32]
     label]))

(defn alarms [alarms-cursor]
  (fn []
    [:div {:style {:display :flex :flex-direction :column}}
     [:h2 "Alarms"]
     [alarm-line (:homing_failed @alarms-cursor) "Homing failed"]
     [alarm-line (:limit_switch_hit_unexpectedly @alarms-cursor) "Limit switch hit unexpectedly"]
     ]))

(defn positions-and-home [stepperX-cursor stepperZ-cursor]
  (fn []
    (let [position-x (:position_inches @stepperX-cursor)
          position-z (:position_inches @stepperZ-cursor)]
      [:h2 "Current Position"]
      [:div {:class "positions-and-home" :style {:display :flex :align-items :center}}
       [:div {:style {:font-size "24px" :margin "16px"}}
        [:div {} (if position-x
                   (str "X: " (goog.string/format "%.3f" position-x))
                   "Not homed")]
        [:div {} (if position-z
                   (str "Z: " (goog.string/format "%.3f" position-z))
                   "Not homed")]]
       [:button {:style {:width "64px" :height "64px"}
                 :on-click (graphql/graphql-fn {:query (str "mutation{home{alarms{" graphql/alarm-keys "}}}")
                                                :handler-fn (fn [resp] (println "home resp" resp))})}
        [svg/home {}
         "white" 32] "Home"]
       ])))

(defn jar-jog-control [alarms-cursor]
  (let [query-fn (fn [jar] (str "mutation{move_to_jar(jar:" jar "){position," graphql/alarms-subquery "}}"))]
    (fn []
      [:div
       [:h2 "Move to jar"]
       (map (fn [jar]
              ^{:key jar} [:button {:on-click (graphql/graphql-fn {:query-fn (partial query-fn jar)
                                                                   :handler-fn (fn [resp]
                                                                                 (reset! atoms/alarms-cursor
                                                                                         (get-in resp [:move_to_jar :alarms])))})}
            (str "Jar #" jar)])
            (range 1 7))
       ])))

(defn up-down-control []
  (fn []
    [:div
     [:h2 "Move slide holder"]
     [:button {:on-click (graphql/graphql-fn {:query "mutation{move_to_up_position{position_inches}}"
                                              :handler-fn (fn [] nil)})}
      "Up"]
     [:button {:on-click (graphql/graphql-fn {:query "mutation{move_to_down_position{position_inches}}"
                                              :handler-fn (fn [] nil)})}
      "Down"]]))

(defn graphql-control []
  (let [input  (reagent/atom "")
        output-data (reagent/atom "")
        output-status (reagent/atom "")
        on-change-handler (fn [atm evt]
                            (reset! atm (-> evt .-target .-value)))]
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
       [:button {:on-click (graphql/graphql-fn {:query-fn (fn [] (deref input))
                                                :handler-fn (fn [resp raw-resp]
                                                              (reset! output-status (str raw-resp))
                                                              (reset! output-data (str resp)))})}
        "Run query"]])))

(defn settings-control [ratom back-fn]
  (fn []
    [:div
     [:div {:class "nav-header"}
      [svg/chevron-left {:class "chevron-left" :on-click back-fn} "blue" 36]
      [:h1 "Settings"]]
     [:button {:on-click (fn [e] (println "foo")
                           (slide-stainer.periodic-updater/periodic-updater-two
                                  atoms/screen-cursor slide-stainer.core/queries-to-run-two))} "Foo"]
     [alarms atoms/alarms-cursor]
     [positions-and-home atoms/stepperX-cursor atoms/stepperZ-cursor]
     [jar-jog-control]
     [up-down-control]
     ;(when (:developer @atoms/settings-cursor))
     [graphql-control]
     ]))
