(ns example.kamera-test
  (:require [kamera.figwheel :as kf]
            [clojure.test :refer [deftest testing is]]))

(deftest devcards-test
  (let [build-id "dev"
        opts (-> kf/default-opts
                 (update :default-target merge {:reference-directory "test-resources/kamera"
                                                :screenshot-directory "target/kamera"})
                 ;; enable to turn off headless mode
                 ;; (assoc-in [:chrome-options :chrome-args] [])
                 )]

    (kf/test-devcards build-id opts)))
