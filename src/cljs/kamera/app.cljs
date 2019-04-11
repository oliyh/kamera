(ns ^:figwheel-hooks kamera.app
  (:require [reagent.core :as reagent]
            [cljs.reader :refer [read-string]]))

(def app (js/document.getElementById "app"))

(def results-store (reagent/atom nil))

(defn- test-result [result]
  (let [child-style {:style {:flex-grow 0
                             :flex-shrink 0
                             :flex-basis "33%"
                             :text-align "center"}}
        chain (:normalisation-chain result)]
    [:div.result

     [:h4 [:a {:name (:expected result)} (:expected result)]
      " - " (if (:passed? result) "passed" "failed")]

     [:div.detail
      (get-in result [:target :metric]) ": "
      (:metric result) " actual / "
      (get-in result [:target :metric-threshold]) " expected"]

     [:div.normalisation-chain
      (for [{:keys [expected actual normalisation] :as n} chain
            :let [show-diff? (= n (last chain))]]

        [:div.normalisation {:key normalisation}
         [:h6 (name normalisation)]
         [:div.comparison {:style {:display "flex"
                                   :flex-direction "row"
                                   :flex-wrap "nowrap"
                                   :justify-content "center"
                                   :align-items "flex-start"}}

          [:div.expected child-style
           [:h6 "Expected"]
           [:img {:src expected
                  :style {:width "100%"}}]]

          [:div.difference child-style
           (when show-diff?
             [:<>
              [:h6 "Difference"]
              [:img {:src (:difference result)
                     :style {:width "100%"}}]])]

          [:div.actual child-style
           [:h6 "Actual"]
           [:img {:src actual
                  :style {:width "100%"}}]]]])]]))

(defn- hello-world []
  (let [{:keys [results]} @results-store]
    [:div
     [:h1 "kamera"]
     [:h3 "Summary"]
     [:div "Ran " (count results) " tests"]
     [:div "Passed: " (count (filter :passed? results))]
     [:div "Failed: " (count (remove :passed? results))]
     [:div [:ul
            (doall (for [{:keys [expected]} results]
                     [:li {:key expected}
                      [:a {:href (str "#" expected)}
                       expected]]))]]

     (doall (for [result results]
              ^{:key (:expected result)}
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
