(ns kamera.graphs-test
  (:require [kamera.graphs :refer [log-gauge]]
            [devcards.core :refer-macros [defcard-rg]]))

(defcard-rg log-gauge-test
  [:div
   [:h4 "Nil"]
   [log-gauge nil]

   (for [v [0 0.0001 0.001 0.01 0.1 1]
         :let [threshold (* v 1.3)]]
     [:div {:key v}
      [:h4 "Value: " v]
      [log-gauge threshold v]])

   [:h4 "Easily green"]
   [log-gauge 0.1 0.001]

   [:h4 "Close to red"]
   [log-gauge 0.1 0.09165466]

   [:h4 "Massively red"]
   [log-gauge 0.1 0.4]
])
