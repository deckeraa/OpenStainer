(ns slide-stainer.atoms
  (:require
   [reagent.core :as reagent]
   [devcards.core])
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]
   [devcards.core :refer [defcard defcard-rg]]))

(defonce app-state
  (reagent/atom {:alarms {}
                 :current-procedure nil
                 :procedure_run_status {}
                 :screen-stack [:main]
                 :stepperX {}
                 :stepperZ {}}))

(defonce procedure-cursor            (reagent/cursor app-state [:current-procedure]))
(defonce procedure-run-status-cursor (reagent/cursor app-state [:procedure_run_status]))
(defonce screen-cursor               (reagent/cursor app-state [:screen-stack]))
(defonce stepperX-cursor             (reagent/cursor app-state [:stepperX]))
(defonce stepperZ-cursor             (reagent/cursor app-state [:stepperZ]))
(defonce alarms-cursor               (reagent/cursor app-state [:alarms]))