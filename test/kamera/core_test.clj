(ns kamera.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.java.io :as io]
            [kamera.core :refer [compare-images dimensions default-opts append-suffix]]))

(deftest image-comparison-test
  (testing "can compare identical images"
    (let [expected (io/file "test-resources/a.png")]
      (is (= {:metric     0
              :expected   (.getAbsolutePath expected)
              :actual     (.getAbsolutePath expected)
              :difference (.getAbsolutePath (io/file "target/a-difference.png"))}

             (compare-images expected
                             expected
                             {:screenshot-directory "target"}
                             default-opts)))))

  (testing "can compare different images that have the same dimensions"
    (let [expected (io/file "test-resources/a.png")
          actual (io/file "test-resources/b.png")]
      (is (= {:metric     4.84915E-4
              :expected   (.getAbsolutePath expected)
              :actual     (.getAbsolutePath actual)
              :difference (.getAbsolutePath (io/file "target/a-difference.png"))}

             (compare-images expected
                             actual
                             {:screenshot-directory "target"}
                             default-opts)))))

  (testing "can compare different images that have different dimensions"
    (testing "when actual is bigger than expected"
      (let [expected (io/file "test-resources/c.png")
            actual (io/file "test-resources/a.png")
            [expected-width expected-height] (dimensions expected default-opts)
            [actual-width actual-height] (dimensions actual default-opts)]

        (is (< expected-width actual-width))
        (is (< expected-height actual-height))

        (is (= {:metric              0
                :expected            (.getAbsolutePath expected)
                :actual              (.getAbsolutePath actual)
                :actual-normalised   (.getAbsolutePath (io/file "target/a-cropped.png"))
                :difference          (.getAbsolutePath (io/file "target/c-difference.png"))}

               (compare-images expected
                               actual
                               {:screenshot-directory "target"}
                               default-opts)))))

    (testing "when expected is bigger than actual"
      (let [expected (io/file "test-resources/a.png")
            actual (io/file "test-resources/c.png")
            [expected-width expected-height] (dimensions expected default-opts)
            [actual-width actual-height] (dimensions actual default-opts)]

        (is (< actual-width expected-width))
        (is (< actual-height expected-height))

        (is (= {:metric              0
                :expected            (.getAbsolutePath expected)
                :expected-normalised (.getAbsolutePath (io/file "target/a-cropped.png"))
                :actual              (.getAbsolutePath actual)
                :difference          (.getAbsolutePath (io/file "target/a-difference.png"))}

               (compare-images expected
                               actual
                               {:screenshot-directory "target"}
                               default-opts))))))

  (testing "fails when an image doesn't exist"
    (let [expected (io/file "test-resources/a.png")
          actual (io/file "test-resources/non-existent.png")
          diff (io/file "target/a_non-existent.png")
          result (compare-images expected
                                 actual
                                 {:screenshot-directory "target"}
                                 default-opts)]
      (is (= {:expected   (.getAbsolutePath expected)
              :actual     (.getAbsolutePath actual)
              :metric 1}

             (dissoc result :errors)))

      (is (:errors result)))))

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
