(ns ^:figwheel-hooks kamera.app
  (:require [reagent.core :as reagent]
            [cljs.reader :refer [read-string]]
            [clojure.string :as string]
            [goog.object :as o]))

(def app (js/document.getElementById "app"))

(def results-store (reagent/atom nil))

(defn- image [url]
  [:a {:href url
       :target "_blank"}
   [:img {:src url}]])

(defn- normalisation-step []
  (let [expanded? (reagent/atom false)]
    (fn [{:keys [expected actual normalisation]}]
      [:div.normalisation
       [:h6.step-name
        {:class (if @expanded? "expanded" "contracted")
         :on-click #(swap! expanded? not)}
        (string/capitalize (name normalisation))]
       [:div.comparison.mdl-grid
        {:class (when-not @expanded? "contracted")}

        [:div.expected.mdl-cell.mdl-cell--4-col
         [image expected]]

        [:div.actual.mdl-cell.mdl-cell--4-col.mdl-cell--4-offset
         [image actual]]]])))

(defn test-result [result]
  (let [chain (:normalisation-chain result)]
    [:div.test-result.mdl-card.mdl-shadow--2dp

     [:div.mdl-card__title
      [:h4.mdl-card__title-text
       [:i.material-icons
        {:class (if (:passed? result) "passed" "failed")}
        (if (:passed? result) "check_circle_outline" "cancel")]
       [:a.test-name {:name (:expected result)} (:expected result)]]]

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
         [:h6.step-name.expanded "Result"]
         [:div.comparison.mdl-grid
          [:div.expected.mdl-cell.mdl-cell--4-col
           [image expected]]

          [:div.difference.mdl-cell.mdl-cell--4-col
           [image (:difference result)]]

          [:div.actual.mdl-cell.mdl-cell--4-col
           [image actual]]]])]]))

(defn- test-list [results]
  [:ul.test-list
   (doall (for [{:keys [expected passed?]} results]
            [:li {:key expected
                  :class (if passed? "passed" "failed")}
             [:a {:href (str "#" expected)}
              expected]]))])

(defn summary [results]
  [:div.summary.mdl-card.mdl-shadow--2dp
   [:div.mdl-card__title
    [:h2.mdl-card__title-text
     "Summary"]]
   [:div.mdl-card__supporting-text
    [:div.mdl-grid.stats
     [:div.mdl-cell.mdl-cell--4-col
      [:div.headline (count results)] "tests"]
     [:div.passed.mdl-cell.mdl-cell--4-col
      [:div.headline (count (filter :passed? results))]
      [:i.material-icons "check_circle_outline"]
      "passed"]
     [:div.failed.mdl-cell.mdl-cell--4-col
      [:div.headline (count (remove :passed? results))]
      [:i.material-icons "cancel"]
      "failed"]]
    [:div
     [:h4 "Tests"]
     [test-list results]]]])

(defn- floating-menu []
  (let [expanded? (reagent/atom false)]
    (fn [results]
      [:div.floating-menu
       {:on-mouse-leave (fn [] (reset! expanded? false))
        :class (when @expanded? "expanded")}
       [:div.button-container
        {:on-click (fn [] (.scrollTo js/window 0 0))}
        [:div.top-button
         [:i.material-icons "eject"]
         [:span.description "top"]]]
       [:div.button-container
        {:on-click (fn [] (swap! expanded? not))}
        [:div.list-button
         [:i.material-icons "list"]
         [:span.description "tests"]]
        (when @expanded?
          [test-list results])]])))

(defn- kamera-report []
  (let [{:keys [results]} @results-store]
    [:div
     [floating-menu results]
     [:h1.title.mdl-shadow--2dp
      {:class (if (every? :passed? results) "passed" "failed")}
      "kamera"]

     [:div.mdl-grid
      [:div.mdl-cell.mdl-cell--12-col
       [summary results]]

      (doall (for [result results]
               ^{:key (:expected result)}
               [:div.mdl-cell.mdl-cell--12-col
                [test-result result]]))]]))

(defn- mount-app []
  (when app
    (reagent/render [kamera-report] app)))

(defn- load-results! []
  (when-let [r (o/get js/window "results")]
    (->> r read-string (reset! results-store))))

(defn- init []
  (load-results!)
  (mount-app))

(defn ^:after-load on-figwheel-reload []
  (load-results!)
  (mount-app))

(.addEventListener js/document "DOMContentLoaded" init)

;; todo
;; 2. Sorting tests by name / difference metric
;; 3. Filtering - all or failed only - should filter list at the top as well as cards below
;; 4. icon / branding in the header
;; 5. display the actual vs expected metric graphically - a gauge?
;; 6. display w x h in pixels on each image
;; 7. tests can have names - generate better ones in devcards
;; 8. errors should bubble into the report
;; 9. magnifier on images
