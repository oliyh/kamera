(ns kamera.report
  (:require [clojure.pprint :refer [pprint]]
            [clojure.java.io :as io]))

;; todo results need to have:
;; - a name (rather than just a filename) - can be optional
;; - all the normalisation steps, with names

(defn- relativize-file [reference-path file]
  (when file
    (str (.relativize (.toPath (.getAbsoluteFile (io/file reference-path)))
                      (.toPath (.getAbsoluteFile (io/file file)))))))

(defn- relativize-files [reference-path results]
  (map (fn [result]
         (reduce (fn [r k]
                   (update r k (partial relativize-file reference-path)))
                 result
                 [:expected :expected-normalised :actual :actual-normalised :difference]))
       results))

(defn write-report! [results opts]
  (let [target-dir (get-in opts [:default-target :screenshot-directory])
        results (relativize-files target-dir results)]
    (spit (io/file target-dir "results.edn")
          (with-out-str (pprint {:results results})))
    (.delete (io/file target-dir "index.html"))
    (io/copy (io/file (io/resource "public/index.html"))
             (io/file target-dir "index.html"))))

(comment
  (def r (let [server (start-jetty)]
           (try
             (k/run-test
              {:url "http://localhost:3000"
               :reference-file "hello-world.png"}
              (-> k/default-opts
                  (update :default-target merge {:reference-directory "test-resources"
                                                 :screenshot-directory "target/public"})))
             (finally
               (.stop server)))))
  (kamera.report/write-report! [r] {:default-target {:screenshot-directory "target/public"}}))
