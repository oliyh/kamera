(ns kamera.graphs)

(defn log-gauge [v]
  (let [radius 0.8
        circumference (* 2 js/Math.PI radius)
        value-on-circumference (* (- 1 v)
                                  (/ circumference 2))]
    [:svg {:class "gauge"
           :viewBox "-1 0 2 1"
           :style {:transform "rotateX(180deg)"}}

     ;; outline
     [:circle {:r radius
               :cx 0
               :cy 0
               :stroke "#f6f6f6"
               :stroke-width 0.22
               :fill "none"
               :stroke-dasharray (str (/ circumference 2) ", " circumference)}]

     ;; low
     [:circle {:r radius
               :cx 0
               :cy 0
               :stroke "#FDE47F"
               :stroke-width 0.2
               :fill "none"
               :stroke-dasharray (str (/ circumference 2) ", " circumference)}]
     ;; medium
     [:circle {:r radius
               :cx 0
               :cy 0
               :stroke "#7CCCE5"
               :stroke-width 0.2
               :fill "none"
               :stroke-dasharray (str (/ circumference 3) ", " circumference)}]

     ;; high
     [:circle {:r radius
               :cx 0
               :cy 0
               :stroke "#E04644"
               :stroke-width 0.2
               :fill "none"
               :stroke-dasharray (str (/ circumference 6) ", " circumference)}]

     ;; mask
     [:circle {:r radius
               :cx 0
               :cy 0
               :stroke "#f9f9f9"
               :stroke-width 0.22
               :fill "none"
               :stroke-dasharray (str value-on-circumference ", " circumference)}]

     ;; outline ends
     [:circle {:r radius
               :cx 0
               :cy 0
               :stroke "#f6f6f6"
               :stroke-width 0.22
               :fill "none"
               :stroke-dasharray (str  "0.01, " (- (/ circumference 2)
                                                   0.02))}]]))
