(ns kamera.app-test
  (:require [kamera.app :as app]
            [devcards.core :refer-macros [defcard-rg]]))

(defcard-rg summary-test
  [:div
   [:h2 "No tests"]
   [app/summary []]

   [:h2 "All passed"]
   [app/summary [{:expected "The good"
                  :passed? true}
                 {:expected "The bad"
                  :passed? true}
                 {:expected "The ugly"
                  :passed? true}]]

   [:h2 "Some failures"]
   [app/summary [{:expected "The good"
                  :passed? true}
                 {:expected "The bad"
                  :passed? false}
                 {:expected "The ugly"
                  :passed? false}]]])

(defcard-rg result-test
  [:div
   [:h2 "Passing test"]
   [app/test-result {:expected "The good"
                     :passed? true
                     :metric 0.000347
                     :difference "example.core_test.expected.difference.png"
                     :normalisation-chain [{:normalisation :original
                                            :expected "example.core_test.expected.png"
                                            :actual "example.core_test.actual.png"}]
                     :target {:metric "mae"
                              :metric-threshold 0.01}}]

   [:h2 "Failing test"]
   [app/test-result {:expected "The bad"
                     :passed? false
                     :metric 0.0973
                     :difference "example.core_test.expected.difference.png"
                     :normalisation-chain [{:normalisation :original
                                            :expected "example.core_test.expected.png"
                                            :actual "example.core_test.actual.png"}]
                     :target {:metric "mae"
                              :metric-threshold 0.01}}]

   [:h2 "Long chain"]
   [app/test-result {:expected "The ugly"
                     :passed? false
                     :metric 0.2973
                     :difference "example.core_test.expected.difference.png"
                     :normalisation-chain [{:normalisation :original
                                            :expected "example.core_test.expected.png"
                                            :actual "example.core_test.actual.png"}
                                           {:normalisation :trimmed
                                            :expected "example.core_test.expected.trimmed.png"
                                            :actual "example.core_test.actual.trimmed.png"}
                                           {:normalisation :cropped
                                            :expected "example.core_test.expected.trimmed.cropped.png"
                                            :actual "example.core_test.actual.trimmed.png"}]
                     :target {:metric "mae"
                              :metric-threshold 0.01}}]])
