(ns kamera.graphs-test
  (:require [kamera.graphs :refer [log-gauge]]
            [devcards.core :refer-macros [defcard-rg]]))

(defcard-rg log-gauge-test
  [:div
   [:h4 "Nil"]
   [log-gauge nil]

   [:h4 "Zero"]
   [log-gauge 0]

   [:h4 "0.25"]
   [log-gauge 0.25]

   [:h4 "0.5"]
   [log-gauge 0.5]

   [:h4 "0.75"]
   [log-gauge 0.75]

   [:h4 "1"]
   [log-gauge 1]])
