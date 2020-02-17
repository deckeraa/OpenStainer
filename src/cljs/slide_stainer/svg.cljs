(ns slide-stainer.svg
  (:require
   [reagent.core :as reagent]
   [devcards.core])
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]
   [devcards.core :refer [defcard defcard-rg]]))

(defn cog [style color size]
  [:div style
   [:svg {:xmlns "http://www.w3.org/2000/svg" :width size :height size :viewBox "0 0 8 8" :fill color}
    [:path {:d "M3.5 0l-.5 1.19c-.1.03-.19.08-.28.13l-1.19-.5-.72.72.5 1.19c-.05.1-.09.18-.13.28l-1.19.5v1l1.19.5c.04.1.08.18.13.28l-.5 1.19.72.72 1.19-.5c.09.04.18.09.28.13l.5 1.19h1l.5-1.19c.09-.04.19-.08.28-.13l1.19.5.72-.72-.5-1.19c.04-.09.09-.19.13-.28l1.19-.5v-1l-1.19-.5c-.03-.09-.08-.19-.13-.28l.5-1.19-.72-.72-1.19.5c-.09-.04-.19-.09-.28-.13l-.5-1.19h-1zm.5 2.5c.83 0 1.5.67 1.5 1.5s-.67 1.5-1.5 1.5-1.5-.67-1.5-1.5.67-1.5 1.5-1.5z"}]]])

(defn bell [style color size]
  [:div style
   [:svg {:xmlns "http://www.w3.org/2000/svg" :width size :height size :viewBox "0 0 8 8" :fill color}>
    [:path {:d "M4 0c-1.1 0-2 .9-2 2 0 1.04-.52 1.98-1.34 2.66-.41.34-.66.82-.66 1.34h8c0-.52-.24-1-.66-1.34-.82-.68-1.34-1.62-1.34-2.66 0-1.1-.89-2-2-2zm-1 7c0 .55.45 1 1 1s1-.45 1-1h-2z"}]]
])

(defn chevron-left [style color size]
  [:div style
   [:svg {:xmlns "http://www.w3.org/2000/svg" :width size :height size :viewBox "0 0 8 8"}
    [:path {:d "M4 0l-4 4 4 4 1.5-1.5-2.5-2.5 2.5-2.5-1.5-1.5z" :transform "translate(1)"}]]])

(defn home [style color size]
  [:div style
   [:svg {:xmlns "http://www.w3.org/2000/svg" :width size :height size :viewBox "0 0 8 8" :fill color}
    [:path {:d "M4 0l-4 3h1v4h2v-2h2v2h2v-4.03l1 .03-4-3z"}]]])
