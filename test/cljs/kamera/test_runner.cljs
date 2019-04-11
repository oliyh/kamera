;; This test runner is intended to be run from the command line
(ns kamera.test-runner
  (:require
   [kamera.all-tests]
   [figwheel.main.testing :refer [run-tests-async]]))

(defn -main [& args]
  (run-tests-async 5000))
