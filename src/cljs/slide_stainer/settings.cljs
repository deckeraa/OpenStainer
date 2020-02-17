(ns slide-stainer.settings
  (:require
   [reagent.core :as reagent]
   [devcards.core]
   [cljs-http.client :as http]
   [clojure.edn :as edn]
   [slide-stainer.svg :as svg]
   [slide-stainer.graphql :as graphql])
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]
   [devcards.core :refer [defcard defcard-rg]]))

(defn refresh-fn [stepperX-cursor stepperZ-cursor]
  (graphql/graphql-fn {:query (str "{stepperX: axis(id:\"stepperX\"){position_inches}, stepperZ: axis(id:\"stepperZ\"){position_inches}}")
                       :handler-fn (fn [resp]
                                     (swap! stepperX-cursor (fn [v] (merge v (:stepperX resp))))
                                     (swap! stepperZ-cursor (fn [v] (merge v (:stepperZ resp)))))}))

(defn positions-and-home [position-x position-z]
  [:div {:class "positions-and-home" :style {:display :flex :align-items :center}}
   [:div {:style {:font-size "24px" :margin "16px"}}
    [:div {} (str "X: " position-x)]
    [:div {} (str "Z: " position-z)]]
   [:button {:style {:width "64px" :height "64px"}
             :on-click (graphql/graphql-fn {:query (str "mutation{home{alarms{" graphql/alarm-keys "}}}")
                                            :handler-fn (fn [resp] (println "home resp" resp))})}
    [svg/home {}
     "white" 32] "Home"]
   ])

(defn settings-control [ratom back-fn]
  (fn []
    [:div
     [:div {:class "nav-header"}
      [svg/chevron-left {:class "chevron-left" :on-click back-fn} "blue" 36]
      [:h1 "Settings"]]
     [positions-and-home (get-in @ratom [:stepperX :position_inches]) (get-in @ratom [:stepperZ :position_inches])]]))
