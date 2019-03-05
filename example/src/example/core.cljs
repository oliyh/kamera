(ns ^:figwheel-hooks example.core
  (:require
   [goog.dom :as gdom]
   [reagent.core :as reagent :refer [atom]]))

(defn get-app-element []
  (gdom/getElement "app"))

(defn hello-user [break-layout?]
  [:div.mdl-card.mdl-shadow--2dp
   {:style {:width "100%"}}
   [:div.mdl-card__title
    [:h2 "Say hello to Kamera"]]
   [:div.mdl-card__supporting-text
    [:h4
     {:style (merge {:margin-top 0}
                    (when break-layout? {:display "inline-block"}))}
     "Visual testing tools for Clojure"]
    [:i.material-icons
     {:style (merge {:font-size "12em"
                     :color "darkred"}
                    (when-not break-layout?
                      {:float "left"}))}
     "photo_camera"]
    "When data is represented visually for a human to view great care must be taken to present it intuitively, accessibly
and beautifully. This requires skill and time, and above all requires human judgement to attest to its efficacy.
"]])

(defn mount [el]
  (reagent/render-component [hello-user false] el))

(defn mount-app-element []
  (when-let [el (get-app-element)]
    (mount el)))

;; conditionally start your application based on the presence of an "app" element
;; this is particularly helpful for testing this ns without launching the app
(mount-app-element)

;; specify reload hook with ^;after-load metadata
(defn ^:after-load on-reload []
  (mount-app-element)
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
