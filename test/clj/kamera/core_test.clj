(ns kamera.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.java.io :as io]
            [kamera.core :refer [compare-images dimensions default-opts append-suffix] :as k]
            [ring.adapter.jetty :refer [run-jetty]])
  (:import [org.eclipse.jetty.server Server]))

(defn- copy-target [src-file suffix]
  (let [src (io/file src-file)
        dest (append-suffix "target" src suffix)]
    (io/copy src dest)
    dest))

(def default-target (assoc (:default-target default-opts)
                           :screenshot-directory "target"))

(deftest image-comparison-test
  (testing "can compare identical images"
    (let [expected (io/file "test-resources/a.png")]
      (is (= {:metric              0
              :expected            (.getAbsolutePath expected)
              :actual              (.getAbsolutePath expected)
              :difference          (.getAbsolutePath (io/file "target/a.difference.png"))
              :normalisation-chain [{:normalisation :original
                                     :expected (.getAbsolutePath expected)
                                     :actual (.getAbsolutePath expected)}]}

             (compare-images expected
                             expected
                             default-target
                             default-opts)))))

  (testing "can compare different images that have the same dimensions"
    (let [expected (io/file "test-resources/a.png")
          actual (io/file "test-resources/b.png")]

      (testing "with the default metric"
        (is (= {:metric              4.84915E-4
                :expected            (.getAbsolutePath expected)
                :actual              (.getAbsolutePath actual)
                :difference          (.getAbsolutePath (io/file "target/a.difference.png"))
                :normalisation-chain [{:normalisation :original
                                       :expected (.getAbsolutePath expected)
                                       :actual (.getAbsolutePath actual)}]}

               (compare-images expected
                               actual
                               default-target
                               default-opts))))

      (testing "with the RMSE metric"
        (is (= {:metric              0.0142263
                :expected            (.getAbsolutePath expected)
                :actual              (.getAbsolutePath actual)
                :difference          (.getAbsolutePath (io/file "target/a.difference.png"))
                :normalisation-chain [{:normalisation :original
                                       :expected (.getAbsolutePath expected)
                                       :actual (.getAbsolutePath actual)}]}

               (compare-images expected
                               actual
                               (assoc default-target :metric "RMSE")
                               default-opts))))))

  (testing "can compare different images that have different dimensions"
    (testing "when actual is bigger than expected"
      (let [expected (copy-target "test-resources/c.png" ".expected")
            actual (copy-target "test-resources/a.png" ".actual")
            [expected-width expected-height] (dimensions expected default-opts)
            [actual-width actual-height] (dimensions actual default-opts)]

        (is (< expected-width actual-width))
        (is (< expected-height actual-height))

        (is (= {:metric              0
                :expected            (.getAbsolutePath (io/file "target/c.expected.trimmed.png"))
                :actual              (.getAbsolutePath (io/file "target/a.actual.trimmed.cropped.png"))
                :difference          (.getAbsolutePath (io/file "target/c.expected.difference.png"))
                :normalisation-chain
                [{:normalisation :original
                  :expected      (.getAbsolutePath expected)
                  :actual        (.getAbsolutePath actual)}
                 {:normalisation :trim
                  :expected      (.getAbsolutePath (io/file "target/c.expected.trimmed.png"))
                  :actual        (.getAbsolutePath (io/file "target/a.actual.trimmed.png"))}
                 {:normalisation :crop
                  :expected      (.getAbsolutePath (io/file "target/c.expected.trimmed.png"))
                  :actual        (.getAbsolutePath (io/file "target/a.actual.trimmed.cropped.png"))}]}

               (compare-images expected
                               actual
                               (assoc (:default-target default-opts) :screenshot-directory "target")
                               default-opts)))))

    (testing "when expected is bigger than actual"
      (let [expected (copy-target "test-resources/a.png" ".expected")
            actual (copy-target "test-resources/c.png" ".actual")
            [expected-width expected-height] (dimensions expected default-opts)
            [actual-width actual-height] (dimensions actual default-opts)]

        (is (< actual-width expected-width))
        (is (< actual-height expected-height))

        (is (= {:metric              0
                :expected            (.getAbsolutePath (io/file "target/a.expected.trimmed.cropped.png"))
                :actual              (.getAbsolutePath (io/file "target/c.actual.trimmed.png"))
                :difference          (.getAbsolutePath (io/file "target/a.expected.difference.png"))
                :normalisation-chain
                [{:normalisation :original
                  :expected      (.getAbsolutePath expected)
                  :actual        (.getAbsolutePath actual)}
                 {:normalisation :trim
                  :expected      (.getAbsolutePath (io/file "target/a.expected.trimmed.png"))
                  :actual        (.getAbsolutePath (io/file "target/c.actual.trimmed.png"))}
                 {:normalisation :crop
                  :expected      (.getAbsolutePath (io/file "target/a.expected.trimmed.cropped.png"))
                  :actual        (.getAbsolutePath (io/file "target/c.actual.trimmed.png"))}]}

               (compare-images expected
                               actual
                               default-target
                               default-opts))))))

  (testing "fails when imagemagick command fails"
    (with-redefs [k/magick (fn [op & args]
                             (if (= "compare" op)
                               {:exit-code 2
                                :stdout ""
                                :stderr ""}
                               (apply k/magick op args)))]
      (let [expected (copy-target "test-resources/a.png" ".expected")
            actual (copy-target "test-resources/c.png" ".actual")
            result (compare-images expected
                                   actual
                                   (assoc default-target :normalisations [])
                                   default-opts)]

        (is (= {:metric              1
                :expected            (.getAbsolutePath (io/file "target/a.expected.png"))
                :actual              (.getAbsolutePath (io/file "target/c.actual.png"))
                :normalisation-chain [{:normalisation :original
                                       :expected      (.getAbsolutePath expected)
                                       :actual        (.getAbsolutePath actual)}]}
               (dissoc result :errors)))

        (is (:errors result)))))

  (testing "fails when an image doesn't exist"
    (let [expected (io/file "test-resources/a.png")
          actual (io/file "test-resources/non-existent.png")
          result (compare-images expected
                                 actual
                                 default-target
                                 default-opts)]
      (is (= {:metric 1
              :expected   (.getAbsolutePath expected)
              :actual     (.getAbsolutePath actual)
              :normalisation-chain
              [{:normalisation :original
                :expected      (.getAbsolutePath expected)
                :actual        (.getAbsolutePath actual)}]}
             (dissoc result :errors)))

      (is (= [(format "Actual is missing: %s" (.getAbsolutePath actual))]
             (:errors result))))))

(deftest screenshot-comparison-test
  (testing "real life difference in screenshots where margins are different"
    (let [expected (copy-target "test-resources/d1.png" ".expected")
          actual (copy-target "test-resources/d2.png" ".actual")
          result (compare-images expected
                                 actual
                                 default-target
                                 default-opts)]
      (is (= {:metric              0.00189661,
              :actual              (.getAbsolutePath (append-suffix actual ".trimmed.cropped"))
              :expected            (.getAbsolutePath (append-suffix expected ".trimmed.cropped"))
              :difference          (.getAbsolutePath (append-suffix expected ".difference"))
              :normalisation-chain
              [{:normalisation :original
                :expected      (.getAbsolutePath (io/file "target/d1.expected.png"))
                :actual        (.getAbsolutePath (io/file "target/d2.actual.png"))}
               {:normalisation :trim
                :expected      (.getAbsolutePath (io/file "target/d1.expected.trimmed.png"))
                :actual        (.getAbsolutePath (io/file "target/d2.actual.trimmed.png"))}
               {:normalisation :crop
                :expected      (.getAbsolutePath (io/file "target/d1.expected.trimmed.cropped.png"))
                :actual        (.getAbsolutePath (io/file "target/d2.actual.trimmed.cropped.png"))}]}

             result)))))

(deftest dimensions-test
  (is (= [800 840]
         (dimensions (io/file "test-resources/c.png") default-opts))))

(deftest append-suffix-test
  (is (= "baz.foo_bar.c-diff.png"
         (.getName (append-suffix (io/file "baz.foo_bar.c.png") "-diff"))))

  (let [renamed (append-suffix (io/file "foo/bar/baz.png") "-diff")]
    (is (= "baz-diff.png" (.getName renamed)))
    (is (= "foo/bar" (.getParent renamed))))

  (let [renamed (append-suffix "target" (io/file "foo/bar/baz.png") "-diff")]
    (is (= "baz-diff.png" (.getName renamed)))
    (is (= "target" (.getParent renamed)))))

(defn- ^Server start-jetty []
  (run-jetty (fn [request]
               {:status 200
                :headers {"Content-Type" "text/html"}
                :body "<h1>Hello World</h1>"})
             {:port 3000
              :join? false}))

(deftest non-devcards-usecase-test
  (let [server (start-jetty)]
    (try
      (k/run-test
       {:url "http://localhost:3000"
        :reference-file "hello-world.png"}
       (-> k/default-opts
           (update :default-target merge {:reference-directory "test-resources"})))
      (finally
        (.stop server)))))
