(ns kamera.figwheel-test
  (:require [kamera.figwheel :as kf]
            [clojure.test :as test :refer [deftest testing is]]
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

(deftest example-figwheel-test
  (let [test-config (io/file "example/kamera.cljs.edn")
        figwheel-config (-> (read-string (slurp "example/dev.cljs.edn"))
                            (vary-meta (fn [meta]
                                         (-> meta
                                             (update :watch-dirs (fn [watch-dirs]
                                                                   (mapv #(str "example/" %) watch-dirs)))
                                             (update :css-dirs (fn [css-dirs]
                                                                 (mapv #(str "example/" %) css-dirs)))))))
        target-dir "example/target/kamera"]

    (delete-dir target-dir)

    (binding [*print-meta* true]
      (spit test-config (prn-str figwheel-config)))

    (let [build-id "example/kamera"
          opts (-> kf/default-opts
                   (assoc :init-hook (fn [session]
                                       (is session "Init hook was called")))
                   (update :default-target merge {:reference-directory "example/test-resources/kamera"
                                                  :screenshot-directory target-dir})
                   (assoc-in [:chrome-options :chrome-args] ["--headless" "--window-size=1280,1024"]))]

      (let [passes (atom [])
            failures (atom [])
            original-report-methods (select-keys (methods test/report) [:fail :pass])]

        (defmethod test/report :pass [m]
          (swap! passes conj m))

        (defmethod test/report :fail [m]
          (swap! failures conj m))

        (try
          (kf/test-devcards build-id opts)
          (finally
            (reinstall-methods test/report original-report-methods)))

        (is (= 1 (count @failures)))
        (is (re-find #"example.another_core_test.png has diverged from reference by \d+\.\d+"
                     (-> @failures first :message))
            "A failure message for example.another_core_test")

        (is (= 2 (count @passes)))
        (is (= "Init hook was called" (-> @passes first :message)))

        (is (re-find #"example.core_test.png has diverged from reference by 0"
                     (-> @passes second :message))
            "A zero divergence for example.core_test")))

    (io/delete-file test-config)))
