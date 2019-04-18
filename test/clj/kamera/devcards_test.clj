(ns kamera.devcards-test
  (:require [kamera.devcards :as kd]
            [clojure.test :as test :refer [deftest testing is use-fixtures]]
            [clojure.java.io :as io])
  (:import [java.io File]))

(defn- delete-dir [d]
  (let [d (io/file d)]
    (when (.exists d)
      (doseq [f (.list (io/file d))]
        (io/delete-file (io/file d f)))
      (io/delete-file (io/file d)))))

(defn- reinstall-methods [multi-fn methods]
  (doseq [[dispatch-val method] methods]
    (remove-method multi-fn dispatch-val)
    (.addMethod multi-fn dispatch-val method)))

(def build-id "example/kamera")
(def target-dir "example/target/kamera")

(defn- prepare-example-project [f]
  (let [test-config (io/file (str build-id ".cljs.edn"))
        config (-> (read-string (slurp "example/dev.cljs.edn"))
                   (vary-meta (fn [meta]
                                (-> meta
                                    (update :watch-dirs (fn [watch-dirs]
                                                          (mapv #(str "example/" %) watch-dirs)))
                                    (update :css-dirs (fn [css-dirs]
                                                        (mapv #(str "example/" %) css-dirs)))))))]

    (binding [*print-meta* true]
      (spit test-config (prn-str config)))

    (delete-dir target-dir)

    (f)

    (io/delete-file test-config)))

(use-fixtures :once prepare-example-project)

(deftest example-figwheel-test
  (let [opts (-> kd/default-opts
                 (update :devcards-options merge
                         {:init-hook (fn [session]
                                       (is session "init-hook was called"))
                          :on-targets (fn [targets]
                                        (is (= 2 (count targets)) "on-targets was called")
                                        targets)})
                 (update :default-target merge {:reference-directory "example/test-resources/kamera"
                                                :screenshot-directory target-dir
                                                :metric-threshold 0.05}))]

    (let [passes (atom [])
          failures (atom [])
          original-report-methods (select-keys (methods test/report) [:fail :pass])]

      (defmethod test/report :pass [m]
        (swap! passes conj m))

      (defmethod test/report :fail [m]
        (swap! failures conj m))

      (try
        (kd/test-devcards build-id opts)
        (finally
          (reinstall-methods test/report original-report-methods)))

      (is (= 1 (count @failures)))
      (is (re-find #"example.another_core_test.png has diverged from reference by \d+\.\d+"
                   (-> @failures first :message))
          "A failure message for example.another_core_test")

      (is (= 3 (count @passes)))
      (is (= "init-hook was called" (-> @passes first :message)))
      (is (= "on-targets was called" (-> @passes second :message)))

      (is (re-find #"example.core_test.png has diverged from reference by 0"
                   (-> @passes last :message))
          "A zero divergence for example.core_test"))))

(deftest generate-initial-screenshots-test
  (testing "when there are no reference images"
    (let [opts (-> kd/default-opts
                   (update :default-target merge {:reference-directory "target"
                                                  :screenshot-directory target-dir}))]

      (let [passes (atom [])
            failures (atom [])
            original-report-methods (select-keys (methods test/report) [:fail :pass])]

        (defmethod test/report :pass [m]
          (swap! passes conj m))

        (defmethod test/report :fail [m]
          (swap! failures conj m))

        (try
          (kd/test-devcards build-id opts)
          (finally
            (reinstall-methods test/report original-report-methods)))

        (testing "all the tests fail"
          (is (= 2 (count @failures)))
          (is (re-find #"Expected: Missing" (:message (first @failures))))
          (is (= 0 (count @passes)))))

      (testing "the actual files are written to the target directory"
        (let [actual-files (.list (io/file target-dir))]
          (is (= (every? #{"example.another_core_test.actual.png"
                           "example.core_test.actual.png"
                           "index.html"
                           "results.edn"
                           "results.js"}
                         actual-files))))))))
