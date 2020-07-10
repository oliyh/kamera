(ns assert-example-results
  (:require [clojure.edn :as edn]
            [clojure.test :refer [deftest testing is run-tests successful?]]))

(deftest example-report-test []
  (let [{:keys [results] :as report} (edn/read-string (slurp "example/target/kamera/results.edn"))]
    (is report)
    (is (= 2 (count results)))

    (testing "first devcard fails on purpose"
      (let [result (first results)]
        (is (false? (:passed? result)))
        (is (pos? (:metric result)))
        (is (= "example.another_core_test.png"
               (get-in result [:target :reference-file])))))

    (testing "second devcard passes"
      (let [result (second results)]
        (is (true? (:passed? result)))
        (is (< (:metric result) 0.05))
        (is (= "example.core_test.png"
               (get-in result [:target :reference-file])))))))

(defn -main [& args]
  (let [test-report (run-tests 'assert-example-results)]
    (println "Example project results:" test-report)
    (when-not (successful? test-report)
      (System/exit 1))))
