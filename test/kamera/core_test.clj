(ns kamera.core-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [kamera.core :refer [compare-images default-opts]]))

(deftest image-comparison-test
  (testing "can compare identical images"
    (is (= {:metric     0
            :expected   "/home/oliy/dev/kamera/test-resources/a.png"
            :actual     "/home/oliy/dev/kamera/test-resources/a.png"
            :difference "/home/oliy/dev/kamera/target/a_a.png"}

           (compare-images (io/file "test-resources/a.png")
                           (io/file "test-resources/a.png")
                           (io/file "target/a_a.png")
                           default-opts))))

  (testing "can compare different images that have the same dimensions"
    (is (= {:metric     4.84915E-4
            :expected   "/home/oliy/dev/kamera/test-resources/a.png"
            :actual     "/home/oliy/dev/kamera/test-resources/b.png"
            :difference "/home/oliy/dev/kamera/target/a_b.png"}

           (compare-images (io/file "test-resources/a.png")
                           (io/file "test-resources/b.png")
                           (io/file "target/a_b.png")
                           default-opts))))

  #_(testing "can compare different images that have different dimensions"
    (is (= {:metric     4.84915E-4
            :expected   "/home/oliy/dev/kamera/test-resources/a.png"
            :actual     "/home/oliy/dev/kamera/test-resources/c.png"
            :difference "/home/oliy/dev/kamera/target/a_c.png"}

           (compare-images (io/file "test-resources/a.png")
                           (io/file "test-resources/c.png")
                           (io/file "target/a_c.png")
                           default-opts))))

  (testing "fails when an image doesn't exist"
    (let [result (compare-images (io/file "test-resources/a.png")
                                   (io/file "test-resources/non-existent.png")
                                   (io/file "target/a_non-existent.png")
                                   default-opts)]
      (is (= {:expected   "/home/oliy/dev/kamera/test-resources/a.png"
              :actual     "/home/oliy/dev/kamera/test-resources/non-existent.png"
              :metric 1}

             (dissoc result :errors)))

      (is (:errors result)))))
