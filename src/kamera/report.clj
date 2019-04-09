(ns kamera.report)

(defn- make-report [results]
  ;; todo
  ;; maybe spit json of the results into the html
  ;; or write out results.json and read that from the cljs app -- probably better
  )

(defn write-report! [file results]
  (spit file results))
