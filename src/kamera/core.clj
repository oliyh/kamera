(ns kamera.core
  (:require [me.raynes.conch.low-level :as sh]
            [clojure.java.io :as io]
            [clojure.test :refer [testing is]]
            [figwheel.main.api :as fig-api])
  (:import [io.webfolder.cdp.session Session]
           [java.util.function Predicate]
           [java.io File]))

(defn compare-images [^File expected
                      ^File actual
                      ^File difference
                      {:keys [path-to-imagemagick
                              imagemagick-timeout]}]
  (merge
   {:error 1
    :expected (.getAbsolutePath expected)
    :actual (.getAbsolutePath actual)}
   (let [args [path-to-imagemagick
               "compare" "-verbose" "-metric" "mae" "-compose" "src"
               (.getAbsolutePath expected)
               (.getAbsolutePath actual)
               (.getAbsolutePath difference)]
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
                        stdout stderr)))

     {:error mean-absolute-error
      :difference (.getAbsolutePath difference)})))

(defn- take-screenshot [^Session session file-name {:keys [screenshot-directory]}]
  (let [data (.captureScreenshot session)
        file (io/file screenshot-directory (str file-name "-actual.png"))]
    (if data
      (do (io/make-parents file)
          (doto (io/output-stream file)
            (.write data)
            (.close))
          file)
      (println "Got no data from the screenshot for" file-name))))

(defn- screenshot-devcards [^Session session test-ns {:keys [devcards-root devcards-load-timeout] :as opts}]
  (.navigate session "about:blank") ;; solves a weird bug navigating directly between fragment urls, i think
  (.waitDocumentReady session 1000)

  (.navigate session (str devcards-root "#/" test-ns))
  (.waitDocumentReady devcards-load-timeout)

  (.waitUntil session (reify Predicate
                        (test [this session]
                          (.matches ^Session session "#com-rigsomelight-devcards-main"))))

  (Thread/sleep 500) ;; give devcards a chance to render

  (take-screenshot session test-ns opts))

(defn test-ns [session test-ns {:keys [reference-directory screenshot-directory error-threshold] :as opts}]
  (testing test-ns
    (let [expected (io/file reference-directory (str test-ns "-expected.png"))
          actual (screenshot-devcards session test-ns opts)
          difference (io/file screenshot-directory (str test-ns "-difference.png"))
          {:keys [error] :as report} (compare-images expected actual difference)]

      (is (< error error-threshold)
          (format "%s has diverged from reference by %s, please compare \nExpected: %s \nActual: %s \nDifference: %s"
                  test-ns error (:expected report) (:actual report) (:difference report))))))

(def default-opts
  {:path-to-imagemagick "imagemagick"
   :imagemagick-timeout 2000
   :devcards-root "http://localhost:9500/cards.html" ;; todo can get this out of figwheel?
   :devcards-load-timeout 60000
   :screenshot-directory "target/kamera"
   :reference-directory "test-resources/kamera"
   :error-threshold 0.001})

(defn run-tests [build-id opts]
  (fig-api/start build-id)

  (let [session nil] ;; todo start a doo-chrome-devprotocol session
    (doseq [ns []] ;; todo work out how to collect the test namespaces that have devcards (or scrape them from the devcards ui?)
      (test-ns session ns opts))

    ;; todo stop chrome session
    )

  (fig-api/stop build-id))

;; API
;; test: figwheel build-id
;;   work out how to start figwheel, launch chrome, discover devcards test-namespaces, screenshot each one, compare images, shut it all down
;;   need directory to store difference (otherwise target)
;;   threshold for failure

;; compare-images: expected/actual pair

;; function to replace all the expecteds with current version

;; non-devcards list of things
;; a non-figwheel API where you provide a list of urls or a callback to discover them,
;; a way to name them or a callback to provide the reference image.

;; for use in selenium etc tests, so you can drive through the app and make UI assertions at any point
;; a single call where you provide a url and a reference image
