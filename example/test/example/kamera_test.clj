(ns example.kamera-test
  (:require [kamera.devcards :as kd]
            [clojure.test :refer [deftest testing is]]))

(deftest devcards-test
  (println "Running example project kamera tests")
  (let [build-id "dev"
        opts (-> kd/default-opts
                 (update :default-target merge {:reference-directory "test-resources/kamera"
                                                :screenshot-directory "target/kamera"})
                 ;; enable to turn off headless mode
                 ;; (assoc-in [:chrome-options :chrome-args] [])
                 )]

    (kd/test-devcards build-id opts)))
