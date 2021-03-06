(ns slide-stainer.atoms
  "Defines the global state atom of the app as
  well as several cursors used to access various elements."
  (:require
   [reagent.core :as reagent]
   [devcards.core])
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]
   [devcards.core :refer [defcard defcard-rg]]))

(defonce app-state
  (reagent/atom {:current-procedure nil
                 :procedure_run_status {}
                 :screen-stack [:procedure-selection]
                 :stepperX {}
                 :stepperZ {}
                 :procedure-list []
                 :settings {}
                 :toaster {:toasts [] :old-toasts []}}))

(defonce procedure-cursor            (reagent/cursor app-state [:current-procedure]))
(defonce procedure-run-status-cursor (reagent/cursor app-state [:procedure_run_status]))
(defonce screen-cursor               (reagent/cursor app-state [:screen-stack]))
(defonce stepperX-cursor             (reagent/cursor app-state [:stepperX]))
(defonce stepperZ-cursor             (reagent/cursor app-state [:stepperZ]))
(defonce procedure-list-cursor       (reagent/cursor app-state [:procedure-list]))
(defonce settings-cursor             (reagent/cursor app-state [:settings]))
(defonce toaster-cursor              (reagent/cursor app-state [:toaster]))
