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

;; (defn refresh-fn []
;;   (graphql/graphql-fn {:query (str "{stepperX: axis(id:\"stepperX\"){position_inches}, stepperZ: axis(id:\"stepperZ\"){position_inches}}")
;;                        :handler-fn (fn [resp]
;;                                      (swap! atoms/stepperX-cursor (fn [v] (merge v (:stepperX resp))))
;;                                      (swap! atoms/stepperZ-cursor (fn [v] (merge v (:stepperZ resp)))))}))

(defn refresh-query-fn []
  (str "{stepperX: axis(id:X){positionInches}, stepperZ: axis(id:Z){positionInches}}")
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

;; (defn alarms [alarms-cursor]
;;   (fn []
;;     [:div {:style {:display :flex :flex-direction :column}}
;;      [:h2 "Alarms"]
;;      [alarm-line (:homing_failed @alarms-cursor) "Homing failed"]
;;      [alarm-line (:limit_switch_hit_unexpectedly @alarms-cursor) "Limit switch hit unexpectedly"]
;;      ]))

(defn positions-and-home [stepperX-cursor stepperZ-cursor]
  (fn []
    (let [position-x (:positionInches @stepperX-cursor)
          position-z (:positionInches @stepperZ-cursor)]
      [:h2 "Current Position"]
      [:div {:class "positions-and-home" :style {:display :flex :align-items :center}}
       [:div {:style {:font-size "24px" :margin "16px"}}
        [:div {} (if (and position-x (not (= "Not homed" position-x))) 
                   (str "X: " (goog.string/format "%.3f" position-x))
                   "Not homed")]
        [:div {} (if (and position-z (not (= "Not homed" position-z))) 
                   (str "Z: " (goog.string/format "%.3f" position-z))
                   "Not homed")]]
       [:button {:on-click #(http/post "http://localhost:8000/home")}
        [svg/home {} "white" 32]
        "Home"]
       ])))

(defn jar-jog-control [alarms-cursor]
  (fn []
    [:div
     [:h2 "Move to jar"]
     (map (fn [jar]
            ^{:key jar} [:button {:on-click #(http/post (str "http://localhost:8000/move_to_jar/" jar))}
                         (str "Jar #" jar)])
          (range 1 7))
     ]))

(defn up-down-control []
  (fn []
    [:div
     [:h2 "Move slide holder"]
     [:button {:on-click #(http/post "http://localhost:8000/move_to_up_position")} "Up"]
     [:button {:on-click #(http/post "http://localhost:8000/move_to_down_position")} "Down"]]))

(defn kiosk-control []
  (fn []
    [:div
     [:h2 "Kiosk mode"]
     [:button {:on-click #(http/post "http://localhost:8000/exit_kiosk_mode")} "Restart in non-kiosk mode"]]))

(defn settings-control [ratom back-fn]
  (fn []
    [:div
     [:div {:class "nav-header"}
      [svg/chevron-left {:class "chevron-left" :on-click back-fn} "blue" 36]
      [:h1 "Settings"]]
 ;    [alarms atoms/alarms-cursor]
     [positions-and-home atoms/stepperX-cursor atoms/stepperZ-cursor]
     [jar-jog-control]
     [up-down-control]
     [kiosk-control]
     ]))
