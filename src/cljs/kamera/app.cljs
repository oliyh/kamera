(ns ^:figwheel-hooks kamera.app
  (:require [kamera.images :refer [image]]
            [kamera.graphs :refer [log-gauge]]
            [reagent.core :as reagent]
            [reagent.ratom :as ratom]
            [cljs.reader :refer [read-string]]
            [clojure.string :as string]
            [goog.object :as o]))

(def app (js/document.getElementById "app"))

;; state
(def results-store (reagent/atom nil))
(def sort-key (reagent/atom :expected))
(def failures-only? (reagent/atom false))

(def results
  (ratom/reaction
   (some->> @results-store
            :results
            not-empty
            (sort-by @sort-key)
            (filter (if @failures-only?
                      (complement :passed?)
                      (constantly true))))))

;; renders

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
      [:div.mdl-grid
       [:div.detail.mdl-cell.mdl-cell--6-col
        [:div "Metric: " (get-in result [:target :metric])]
        [:div "Threshold: " (get-in result [:target :metric-threshold])]
        [:div "Result: " (:metric result)]]

       [:div.mdl-cell.mdl-cell--6-col
        [log-gauge
         (get-in result [:target :metric-threshold])
         (:metric result)]]]

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

(defn- test-summary [results]
  [:div
   [:h4 "Tests"]
   [:div.controls
    "Sort: "
    [:select {:value (name @sort-key)
              :on-change (fn [x]
                           (reset! sort-key (keyword (.-value (.-target x)))))}
     (for [[label value] [["Name" :expected]
                          ["Difference" :metric]]]
       [:option {:key value :value value}
        label])]

    " Failures only: "
    [:label.mdl-switch.is-upgraded
     {:class (when @failures-only? "is-checked")
      :for "failures-only?"}
     [:input.mdl-switch__input {:id "failures-only?"
                                :type "checkbox"
                                :defaultChecked @failures-only?
                                :on-change (fn [x]
                                             (reset! failures-only? (.-checked (.-target x))))}]
     [:div.mdl-switch__track]
     [:div.mdl-switch__thumb
      [:span.mdl-switch__focus-helper]]]]
   [test-list results]])

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
    [test-summary results]]])

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
  (let [results @results]
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
;; 4. icon / branding in the header
;; 6. display w x h in pixels on each image
;; 7. tests can have names - generate better ones in devcards
;; 8. errors should bubble into the report
