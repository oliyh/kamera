(ns kamera.graphs
  (:require [clojure.string :as string]))

(defn log-gauge [threshold v]
  (when v
    (let [radius 0.8
          circumference (* 2 js/Math.PI radius)
          semi-c (/ circumference 2)
          scale-min 0.0001
          scale-max 1
          radians js/Math.PI
          tick-marks (map #(js/Math.pow 10 %)
                          (range (js/Math.floor (js/Math.log10 scale-min))
                                 (inc (js/Math.ceil (js/Math.log10 scale-max)))))
          scale-value (fn [v]
                        (min scale-max
                             (max scale-min
                                  (/ (- (js/Math.log10 v) (js/Math.log10 scale-min))
                                     (- (js/Math.log10 scale-max) (js/Math.log10 scale-min))))))
          value-coords (fn [r v]
                         (let [theta (* radians (scale-value v))]
                           {:x (- (* r (js/Math.cos theta)))
                            :y (* r (js/Math.sin theta))}))
          value-on-circumference (* (- 1 (scale-value v)) semi-c)
          amber-threshold 0.9
          amber-sweep (* (- 1 (* amber-threshold (scale-value threshold))) semi-c)
          red-sweep (* (- 1 (scale-value threshold)) semi-c)
          section-width 0.2
          border-width 0.02
          base-attrs {:r radius
                      :cx 0
                      :cy 0
                      :stroke-width section-width
                      :fill "none"}
          fill-colour (cond (< threshold v) "red"
                            (< (* amber-threshold (scale-value threshold))
                               (scale-value v)) "orange"
                            :else "green")
          tick-mark (fn [length v]
                      (let [from (value-coords
                                  (+ radius (/ section-width 2))
                                  v)
                            to (value-coords
                                (+ radius (/ section-width 2) length)
                                v)]
                        [:line {:x1 (:x from) :x2 (:x to)
                                :y1 (:y from) :y2 (:y to)
                                :stroke-width 0.01
                                :stroke "darkgrey"}]))

          tick-text (fn [distance v & [label]]
                      (let [{:keys [x y]} (value-coords
                                           (+ radius (/ section-width 2) distance)
                                           v)]
                        [:text {:x x :y (- y)
                                :text-anchor "middle"
                                :style {:font-size 0.06
                                        :transform "rotateX(180deg)"}}
                         (or label v)]))]

      [:svg {:class "gauge"
             :viewBox "-1.1 0 2.2 1.1"
             :style {:transform "rotateX(180deg)"}}
       ;; outline
       [:circle (merge base-attrs
                       {:stroke "#f6f6f6"
                        :stroke-width (+ section-width border-width)
                        :stroke-dasharray (str semi-c ", " circumference)})]

       ;; low
       [:circle (merge base-attrs
                       {:stroke "green"
                        :stroke-dasharray (str semi-c ", " circumference)})]
       ;; medium
       [:circle (merge base-attrs
                       {:stroke "orange"
                        :stroke-dasharray (str amber-sweep ", " circumference)})]

       ;; high
       [:circle (merge base-attrs
                       {:stroke "red"
                        :stroke-dasharray (str red-sweep ", " circumference)})]

       ;; mask
       [:circle (merge base-attrs
                       {:stroke "rgba(255,255,255,0.7)"
                        :stroke-width (+ section-width border-width)
                        :stroke-dasharray (str value-on-circumference ", " circumference)})]

       ;; outline ends
       [:circle (merge base-attrs
                       {:stroke "#f6f6f6"
                        :stroke-width (+ section-width border-width)
                        :stroke-dasharray (str (/ border-width 2) ", "
                                               (- semi-c 0.02))})]

       ;; tick marks
       (for [t tick-marks]
         ^{:key t} [tick-mark 0.05 t])

       (for [t tick-marks]
         ^{:key t} [tick-text 0.1 t])

       ;; threshold
       ;; [tick-mark 0.15 threshold]
       ;; [tick-text 0.2 threshold "Threshold"]

       ;; value
       (let [theta (* radians (scale-value v))
             {:keys [x y]} (value-coords (- radius (/ section-width 2))
                                         v)]
         [:polygon
          {:style {:transform (str "rotate(" (- theta) "rad)")}
           :stroke-width 0.01
           :stroke "black"
           :fill fill-colour
           :points
           (string/join " "
                        (map #(string/join "," %)
                             [[(- (/ section-width 2) radius)
                               0.0]
                              [(- (/ section-width 2) radius -0.08)
                               0.05]
                              [(- (/ section-width 2) radius -0.08)
                               -0.05]]))}])

       [:text.central-stat
        {:x 0 :y -0.1 :text-anchor "middle"
         :style {:font-size 0.25
                 :fill fill-colour
                 :transform "rotateX(180deg)"}}
        (.toFixed v 4)]
       ])))
