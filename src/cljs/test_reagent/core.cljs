(ns test-reagent.core
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Page

(defn page [ratom]
  [:div
   "Welcome to reagent-figwheel!!!!!!!!!!!"
   [led-button "LED" 17]
   [led-button 18 18]])

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
