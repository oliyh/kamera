(ns ^:figwheel-hooks kamera.app
  (:require [reagent.core :as reagent]
            [cljs.reader :refer [read-string]]))

(def app (js/document.getElementById "app"))

(def results-store (reagent/atom nil))

(defn- test-result [result]
  (let [child-style {:style {:flex-grow 0
                             :flex-shrink 0
                             :flex-basis "33%"
                             :text-align "center"}}]
    [:div.result {:key (:expected result)}

     [:h4 (:expected result) " - " (if (:passed? result) "passed" "failed")]

     [:div.detail
      (get-in result [:target :metric]) ": "
      (:metric result) " actual / "
      (get-in result [:target :metric-threshold]) " expected"]

     [:div.comparison {:style {:display "flex"
                               :flex-direction "row"
                               :flex-wrap "nowrap"
                               :justify-content "center"
                               :align-items "flex-start"}}
      [:div.expected child-style
       [:h6 "Expected"]
       [:img {:src (:expected result)
              :style {:width "100%"}}]]

      [:div.difference child-style
       [:h6 "Difference"]
       [:img {:src (:difference result)
              :style {:width "100%"}}]]

      [:div.actual child-style
       [:h6 "Actual"]
       [:img {:src (:actual result)
              :style {:width "100%"}}]]]]))

(defn- hello-world []
  (let [{:keys [results]} @results-store]
    [:div
     [:h1 "kamera"]
     [:h3 "Summary"]
     [:div "Ran " (count results) " tests"]
     [:div "Passed: " (count (filter :passed? results))]
     [:div "Failed: " (count (remove :passed? results))]

     (doall (for [result results]
              [test-result result]))]))

(defn- mount-app []
  (reagent/render [hello-world] app))

(defn- load-results! []
  (-> (js/fetch "results.edn")
      (.then (fn [response]
               (.text response)))
      (.then (fn [text]
               (reset! results-store (read-string text))))))

(defn- init []
  (load-results!)
  (mount-app))

(defn ^:after-load on-figwheel-reload []
  (load-results!)
  (mount-app))

(.addEventListener js/document "DOMContentLoaded" init)
