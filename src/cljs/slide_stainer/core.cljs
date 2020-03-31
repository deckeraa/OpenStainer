(ns slide-stainer.core
  "Creates the root Reagent control for the slide stainer UI."
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
   [slide-stainer.procedure-edit]
   [slide-stainer.procedure-selection]
   [slide-stainer.procedure-run]
   [slide-stainer.toaster-oven :as toaster-oven])
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]
   [devcards.core :refer [defcard defcard-rg]]))

(defn replace-current-screen
  "Replaces the screen on top of the screen stack with a different screen"
  [screen-cursor new-screen]
  (swap! screen-cursor (fn [v] (conj (pop v) new-screen))))

(defn page
  "Reagent control for the overall page of the UI. This should be the root Reagent component."
  [ratom]
  (fn []
    [:div
     [:div {:class "header"}
      [:h1 "OpenStainer"]
      [:h2 "v1.0.0"]
      [svg/cog {:class "cog" :on-click #(swap! atoms/screen-cursor conj :settings)} "white" 36]]
     [:div {:class "body body-background"}
      (when (= :procedure-selection (peek @atoms/screen-cursor))
        [slide-stainer.procedure-selection/procedure-selection atoms/procedure-list-cursor atoms/procedure-cursor
         #(swap! atoms/screen-cursor conj :procedure-edit)
         ])
      (when (= :procedure-edit (peek @atoms/screen-cursor))
        [slide-stainer.procedure-edit/procedure-edit
         atoms/procedure-cursor
         atoms/procedure-run-status-cursor
         #(swap! atoms/screen-cursor pop)
         (fn [procedure]
           (swap! ratom (fn [v] (-> v
                                    (assoc :current-procedure procedure)
                                    )))
           (swap! atoms/screen-cursor conj :procedure-run))])
      (when (= :procedure-run (peek @atoms/screen-cursor))
        [slide-stainer.procedure-run/procedure-run-status atoms/procedure-cursor
         atoms/procedure-run-status-cursor
         #(swap! atoms/screen-cursor pop) ;; TODO make this stop the currently running staining procedure (maybe?)
         ])
      (when (= :settings (peek @atoms/screen-cursor))
        [slide-stainer.settings/settings-control
         ratom
         (fn []
           ((graphql/graphql-fn {:query (str "mutation{saveSettings(settings:"
                                             (-> @atoms/settings-cursor
                                                 (graphql/jsonify)
                                                 (graphql/remove-quotes-from-keys))
                                             "){" graphql/settings-keys "}}")
                                 :handler-fn (fn [resp]
                                               (when (:settings resp)
                                                 (reset! atoms/settings-cursor (:settings resp))))}))
           (swap! atoms/screen-cursor pop))])
      (when (:developer @atoms/settings-cursor)
        [:div {} (str @ratom)])]
     [toaster-oven/toaster-control]
     ]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Periodic Updater data and instances

(def queries-to-run
  {:init {:query-fn (fn [] (str "{settings{" graphql/settings-keys "},procedures{_id,name,runs}}"))
          :handler-fn (fn [resp]
                        (reset! atoms/settings-cursor (:settings resp))
                        (reset! atoms/procedure-list-cursor (:procedures resp)))
          :should-run? (fn [] (empty? @atoms/settings-cursor))}
   :procedure-run {:rest-fn slide-stainer.procedure-run/rest-fn
                   :rest-handler-fn slide-stainer.procedure-run/rest-handler-fn
                   :query-fn slide-stainer.procedure-run/refresh-query-fn
                   :handler-fn (partial slide-stainer.procedure-run/refresh-handler-fn atoms/procedure-cursor atoms/procedure-run-status-cursor)}
   :settings {:query-fn slide-stainer.settings/refresh-query-fn :handler-fn slide-stainer.settings/refresh-handler-fn}})

(defonce periodic-updater-instance
  (js/setTimeout (fn [] (slide-stainer.periodic-updater/periodic-updater
                         atoms/screen-cursor queries-to-run))
                 (* 1 1000)))

(defonce rest-updater-instance
  (js/setTimeout (fn [] (slide-stainer.periodic-updater/fast-rest-updater
                         atoms/screen-cursor
                         {:procedure-run {:run-query-fn (partial slide-stainer.procedure-run/run-query-fn atoms/procedure-run-status-cursor)
                                          :rest-fn  slide-stainer.procedure-run/rest-fn
                                          :rest-handler-fn (partial slide-stainer.procedure-run/rest-handler-fn
                                                                    atoms/procedure-cursor atoms/procedure-run-status-cursor)}}))
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
