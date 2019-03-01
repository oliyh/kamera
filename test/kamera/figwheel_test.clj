(ns kamera.figwheel-test
  (:require [kamera.figwheel :as kf]
            [clojure.test :refer [deftest testing is]]))

(deftest example-figwheel-test
  (let [build-id "example/dev"
        opts (-> kf/default-opts
                 (update :default-target merge {:reference-directory "example/test-resources/kamera"
                                                :screenshot-directory "example/target/kamera"})
                 ;;(assoc-in [:chrome-options :chrome-args] [])
                 )]

    (kf/test-devcards build-id opts)))
