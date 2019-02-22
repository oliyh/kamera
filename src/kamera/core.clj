(ns kamera.core
  (:require [me.raynes.conch.low-level :as sh]
            [clojure.java.io :as io]
            [clojure.test :refer [testing is]]
            [figwheel.main.api :as fig-api]
            [doo-chrome-devprotocol.core :as dcd])
  (:import [io.webfolder.cdp.session Session]
           [java.util.function Predicate]
           [java.io File]))

(defn compare-images [^File expected
                      ^File actual
                      ^File difference
                      {:keys [path-to-imagemagick
                              imagemagick-args
                              imagemagick-timeout]}]
  (merge
   {:error 1
    :expected (.getAbsolutePath expected)
    :actual (.getAbsolutePath actual)}
   (let [args (reduce into
                     [path-to-imagemagick]
                     [imagemagick-args
                      [(.getAbsolutePath expected)
                       (.getAbsolutePath actual)
                       (.getAbsolutePath difference)]])
         process (apply sh/proc args)
         stdout (sh/stream-to-string process :out)
         stderr (sh/stream-to-string process :err)
         mean-absolute-error (if-let [e (last (re-find #"all: .* \((.*)\)" stderr))]
                               (read-string e)
                               (do (println (format "Could not parse ImageMagick output\n stdout: %s \n stderr: %s"
                                                    stdout stderr))))
         exit-code (sh/exit-code process imagemagick-timeout)]

     (when-not (zero? exit-code)
       (println (format "Error comparing images - ImageMagick exited with code %s \n stdout: %s \n stderr: %s"
                        exit-code stdout stderr)))

     {:error mean-absolute-error
      :difference (.getAbsolutePath difference)})))

(defn- take-screenshot [^Session session {:keys [reference-file screenshot-directory] :as target} opts]
  (let [data (.captureScreenshot session)
        file (io/file screenshot-directory reference-file)]
    (if data
      (do (io/make-parents file)
          (doto (io/output-stream file)
            (.write data)
            (.close))
          file)
      (println "Got no data from the screenshot for" reference-file))))

(defn- screenshot-target [^Session session {:keys [root url load-timeout] :as target} opts]
  (.navigate session "about:blank") ;; solves a weird bug navigating directly between fragment urls, i think
  (.waitDocumentReady session (int 1000))

  (.navigate session (str root url))
  (.waitDocumentReady session (int load-timeout))

  ;; hmm, this might need to be part of the target as well
  (.waitUntil session (reify Predicate
                        (test [this session]
                          (.matches ^Session session "#com-rigsomelight-devcards-main"))))

  (Thread/sleep 500) ;; give devcards a chance to render

  (take-screenshot session target opts))

(defn test-target [session {:keys [root url reference-directory reference-file screenshot-directory error-threshold] :as target} opts]
  (testing url
    (let [expected (io/file reference-directory reference-file)
          actual (screenshot-target session target opts)
          difference (io/file screenshot-directory (str reference-file "-difference.png"))
          {:keys [error] :as report} (compare-images expected actual difference opts)]

      (is (< error error-threshold)
          (format "%s has diverged from reference by %s, please compare \nExpected: %s \nActual: %s \nDifference: %s"
                  reference-file error (:expected report) (:actual report) (:difference report))))))

(def default-opts
  {:path-to-imagemagick "compare"
   :imagemagick-args ["-verbose" "-metric" "mae" "-compose" "src"]
   :imagemagick-timeout 2000
   :default-target {:root "http://localhost:9500/devcards.html"
                    ;; :url must be supplied on each target
                    ;; :reference-file must be supplied on each target
                    :error-threshold 0.01
                    :load-timeout 60000
                    :reference-directory "test-resources/kamera"
                    :screenshot-directory "target/kamera"}})


;; this is the general usecase of there is a website, i want to visit these links and do the comparison
;; could provide a callback to dynamically find the urls, references etc

(defn run-tests
  ([targets opts]
   (dcd/with-chrome-session
     opts
     (fn [session opts]
       (run-tests session targets opts))))
  ([^Session session targets opts]
   (let [default-target (:default-target opts)]
     (doseq [target targets]
       (test-target session (merge default-target target) opts)))))

;; API
;; test: figwheel build-id

;; compare-images: expected/actual pair

;; function to replace all the expecteds with current version

;; non-devcards list of things
;; a non-figwheel API where you provide a list of urls or a callback to discover them,
;; a way to name them or a callback to provide the reference image.

;; for use in selenium etc tests, so you can drive through the app and make UI assertions at any point
;; a single call where you provide a url and a reference image
