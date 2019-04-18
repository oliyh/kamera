(ns ^:figwheel-hooks kamera.app
  (:require [reagent.core :as reagent]
            [cljs.reader :refer [read-string]]))

(def app (js/document.getElementById "app"))

(def results-store (reagent/atom nil))

(defn- normalisation-step []
  (let [expanded? (reagent/atom false)]
    (fn [{:keys [expected actual normalisation]}]
      [:div.normalisation
       [:h6.step-name
        {:on-click #(swap! expanded? not)}
        (name normalisation)]
       [:div.comparison.mdl-grid
        {:class (when-not @expanded? "contracted")}

        [:div.expected.mdl-cell.mdl-cell--4-col
         [:img {:src expected}]]

        [:div.actual.mdl-cell.mdl-cell--4-col.mdl-cell--4-offset
         [:img {:src actual}]]]])))

(defn- test-result [result]
  (let [chain (:normalisation-chain result)]
    [:div.test-result.mdl-card.mdl-shadow--2dp

     [:div.mdl-card__title
      [:h4.mdl-card__title-text
       [:i.material-icons
        (if (:passed? result) "check_circle_outline" "remove_circle_outline")]
       [:a {:name (:expected result)} (:expected result)]]]

     [:div.mdl-card__supporting-text
      [:div.detail
       (get-in result [:target :metric]) ": "
       (:metric result) " actual / "
       (get-in result [:target :metric-threshold]) " expected"]

      [:div.comparison-titles.mdl-grid
       [:div.mdl-cell.mdl-cell--4-col
        [:h6 "Expected"]]

       [:div.mdl-cell.mdl-cell--4-col
        [:h6 "Difference"]]

       [:div.mdl-cell.mdl-cell--4-col
        [:h6 "Actual"]]]

      [:div.normalisation-chain
       (for [step chain]
         ^{:key (:normalisation step)}
         [normalisation-step step])]

      (let [{:keys [expected actual]} (last chain)]
        [:div.final-result
         [:h6.step-name "Result"]
         [:div.comparison.mdl-grid
          [:div.expected.mdl-cell.mdl-cell--4-col
           [:img {:src expected}]]

          [:div.difference.mdl-cell.mdl-cell--4-col
           [:img {:src (:difference result)}]]

          [:div.actual.mdl-cell.mdl-cell--4-col
           [:img {:src actual}]]]])]]))

(defn- summary [results]
  [:div.summary.mdl-card.mdl-shadow--2dp
   [:div.mdl-card__title
    [:h2.mdl-card__title-text
     "Summary"]]
   [:div.mdl-card__supporting-text
    [:div "Ran " (count results) " tests"]
    [:div "Passed: " (count (filter :passed? results))]
    [:div "Failed: " (count (remove :passed? results))]
    [:div
     [:h4 "Tests"]
     [:ul
      (doall (for [{:keys [expected]} results]
               [:li {:key expected}
                [:a {:href (str "#" expected)}
                 expected]]))]]]])

(defn- kamera-report []
  (let [{:keys [results]} @results-store]
    [:div
     [:h1.title.mdl-shadow--2dp "kamera"]

     [:div.mdl-grid
      [:div.mdl-cell.mdl-cell--12-col
       [summary results]]

      (doall (for [result results]
               ^{:key (:expected result)}
               [:div.mdl-cell.mdl-cell--12-col
                [test-result result]]))]]))

(defn- mount-app []
  (reagent/render [kamera-report] app))

(defn- load-results! []
  (->> js/results read-string (reset! results-store)))

(defn- init []
  (load-results!)
  (mount-app))

(defn ^:after-load on-figwheel-reload []
  (load-results!)
  (mount-app))

(.addEventListener js/document "DOMContentLoaded" init)
