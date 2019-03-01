(ns kamera.figwheel-test
  (:require [kamera.figwheel :as kf]
            [clojure.test :refer [deftest testing is]]
            [clojure.java.io :as io]))

(deftest example-figwheel-test
  (let [test-config (io/file "example/kamera.cljs.edn")
        figwheel-config (-> (read-string (slurp "example/dev.cljs.edn"))
                            (vary-meta (fn [meta]
                                         (-> meta
                                             (update :watch-dirs (fn [watch-dirs]
                                                                   (mapv #(str "example/" %) watch-dirs)))
                                             (update :css-dirs (fn [css-dirs]
                                                                   (mapv #(str "example/" %) css-dirs)))))))]
    (binding [*print-meta* true]
      (spit test-config (prn-str figwheel-config)))

    (let [build-id "example/kamera"
          opts (-> kf/default-opts
                   (update :default-target merge {:reference-directory "example/test-resources/kamera"
                                                  :screenshot-directory "example/target/kamera"})
                   (assoc-in [:chrome-options :chrome-args] []))]

      (kf/test-devcards build-id opts))

    (io/delete-file test-config)))
