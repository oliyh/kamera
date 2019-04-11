(ns kamera.core
  (:require [kamera.report :as report]
            [me.raynes.conch.low-level :as sh]
            [clojure.java.io :as io]
            [clojure.test :refer [testing is]]
            [figwheel.main.api :as fig-api]
            [doo-chrome-devprotocol.core :as dcd]
            [clojure.string :as string]
            [clojure.tools.logging :as log])
  (:import [io.webfolder.cdp.session Session]
           [java.util.function Predicate]
           [java.io File]))

(defn magick [operation
              operation-args
              {:keys [imagemagick-options]}]
  (let [{:keys [path timeout]} imagemagick-options
        executable (or (when path
                         (let [f (io/file path)]
                           (if (.isDirectory f)
                             [(.getAbsolutePath (io/file f operation))]
                             [(.getAbsolutePath f) operation])))
                       [operation])
        args (into executable operation-args)
        process (apply sh/proc args)]
    {:stdout (sh/stream-to-string process :out)
     :stderr (sh/stream-to-string process :err)
     :exit-code (sh/exit-code process timeout)}))

(defn ^File append-suffix
  ([^File file suffix] (append-suffix (.getParent file) file suffix))
  ([dir ^File file suffix]
   (let [file-name (.getName file)]
     (io/file dir (string/replace file-name #"\.(\w+)$" (str suffix ".$1"))))))

(defn dimensions [^File image opts]
  (let [{:keys [stdout exit-code]} (magick "convert"
                                           [(.getAbsolutePath image)
                                            "-ping"
                                            "-format"
                                            "%w:%h"
                                            "info:"]
                                           opts)]
    (when (zero? exit-code)
      (mapv #(Long/parseLong (string/trim %)) (string/split stdout #":")))))

(defn crop-images
  ([expected actual opts] (crop-images expected actual opts "+0+0"))
  ([^File expected ^File actual opts crop-anchor]
   (let [expected-dimensions (dimensions expected opts)
         actual-dimensions (dimensions actual opts)
         [target-width target-height] [(apply min (map first [expected-dimensions actual-dimensions]))
                                       (apply min (map second [expected-dimensions actual-dimensions]))]]
     (mapv (fn [[^File file [width height]]]
             (if (or (< target-width width)
                     (< target-height height))
               (let [cropped (append-suffix file ".cropped")]
                 (magick "convert"
                         [(.getAbsolutePath file)
                          "-crop"
                          (format "%sx%s%s" target-width target-height crop-anchor)
                          (.getAbsolutePath cropped)]
                         opts)
                 cropped)
               file))
           [[expected expected-dimensions]
            [actual actual-dimensions]]))))

(defn trim-images
  ([expected actual opts] (trim-images expected actual opts 1))
  ([^File expected ^File actual opts fuzz-percent]
   (mapv (fn [^File file]
           (let [trimmed (append-suffix file ".trimmed")]
             (magick "convert"
                     [(.getAbsolutePath file)
                      "-trim"
                      "+repage"
                      "-fuzz" (str fuzz-percent "%")
                      (.getAbsolutePath trimmed)]
                     opts)
             trimmed))
         [expected actual])))

(defn- normalisation-chain [^File expected ^File actual]
  [{:normalisation :original
    :expected expected
    :actual actual}])

(defn- normalise-images [normalisations ^File expected ^File actual {:keys [normalisation-fns] :as opts}]
  (let [expected-dimensions (dimensions expected opts)
        actual-dimensions (dimensions actual opts)
        chain (normalisation-chain expected actual)]
    (if (and expected-dimensions actual-dimensions
             (not= expected-dimensions actual-dimensions))
      (reduce (fn [acc norm-key]
                (let [{:keys [expected actual]} (last acc)
                      norm-fn (get normalisation-fns norm-key)
                      [e' a'] (norm-fn expected actual opts)]
                  (conj acc {:normalisation norm-key
                             :expected e'
                             :actual a'})))
              chain
              normalisations)
      chain)))

(defn compare-images [^File expected
                      ^File actual
                      {:keys [metric screenshot-directory normalisations]}
                      opts]
  (if-not (and (.exists expected) (.exists actual))
    {:metric 1
     :expected (str (when-not (.exists expected) "Missing - ")
                    (.getAbsolutePath expected))
     :actual (str (when-not (.exists actual) "Missing - ")
                  (.getAbsolutePath actual))
     :errors (->> [(when-not (.exists expected) "Expected is missing")
                   (when-not (.exists actual) "Actual is missing")]
                  (remove nil?)
                  (into []))}

    (merge
     {:metric 1
      :expected (.getAbsolutePath expected)
      :actual (.getAbsolutePath actual)}
     (let [difference (append-suffix screenshot-directory expected ".difference")
           normalisation-chain (try (normalise-images normalisations expected actual opts)
                                    (catch Throwable t
                                      (log/warn "Error normalising images" t)
                                      (normalisation-chain expected actual)))
           {:keys [^File expected ^File actual]} (last normalisation-chain)
           {:keys [stdout stderr exit-code]}
           (magick "compare"
                   ["-verbose" "-metric" metric "-compose" "src"
                    (.getAbsolutePath expected)
                    (.getAbsolutePath actual)
                    (.getAbsolutePath difference)]
                   opts)
           mean-absolute-error (when-let [e (last (re-find #"all: .* \((.*)\)" stderr))]
                                 (read-string e))]

       (merge-with concat
                   {:actual (.getAbsolutePath actual)
                    :expected (.getAbsolutePath expected)
                    :normalisation-chain (map (fn [n]
                                                (-> n
                                                    (update :expected #(.getAbsolutePath %))
                                                    (update :actual #(.getAbsolutePath %))))
                                              normalisation-chain)}

                   (if (not= 2 exit-code)
                     {:difference (.getAbsolutePath difference)}
                     {:errors [(format "Error comparing images - ImageMagick exited with code %s \n stdout: %s \n stderr: %s"
                                       exit-code stdout stderr)]})

                   (if mean-absolute-error
                     {:metric mean-absolute-error}
                     {:errors [(format "Could not parse ImageMagick output\n stdout: %s \n stderr: %s"
                                       stdout stderr)]}))))))

(defn- body-dimensions [^Session session]
  (let [dom (.getDOM (.getCommand session))
        root-node (.getDocument dom)
        body-node-id (.querySelector dom (.getNodeId root-node) "body")
        box-model (.getBoxModel dom body-node-id nil nil)]
    [(.getWidth box-model) (.getHeight box-model)]))

(defn- resize-window-to-contents! [^Session session]
  (let [[width height] (body-dimensions session)
        emulation (.getEmulation (.getCommand session))
        device-scale-factor 1.0
        mobile? false]

    (.setVisibleSize emulation width height)
    (.setDeviceMetricsOverride emulation width height device-scale-factor mobile?)
    (.setPageScaleFactor emulation 1.0)))

(defn- take-screenshot [^Session session {:keys [reference-file screenshot-directory] :as target} opts]
  (resize-window-to-contents! session)

  (let [data (.captureScreenshot session true) ;; hides scrollbar
        file (append-suffix screenshot-directory (io/file reference-file) ".actual")]
    (if data
      (do (io/make-parents file)
          (doto (io/output-stream file)
            (.write data)
            (.close))
          file)
      (log/warn "Got no data from the screenshot for" reference-file))))

(defn element-exists? [selector]
  (fn [^Session session]
    (let [result (.matches session selector)]
      result)))

(defn wait-for [^Session session pred-fn]
  (.waitUntil session (reify Predicate
                        (test [this session]
                          (pred-fn session)))))

(defn- screenshot-target [^Session session {:keys [url load-timeout ready?] :as target} opts]
  (.navigate session "about:blank") ;; solves a weird bug navigating directly between fragment urls, i think
  (.waitDocumentReady session (int 1000))

  (.navigate session url)
  (.waitDocumentReady session (int load-timeout))

  (when ready?
    (wait-for session ready?))

  (Thread/sleep 500) ;; small timeout for any js rendering

  (take-screenshot session target opts))

(defn test-target [session {:keys [url reference-directory reference-file screenshot-directory metric-threshold] :as target} opts]
  (testing url
    (log/info "Testing" target)
    (let [source-expected (io/file reference-directory reference-file)
          expected (let [ex (append-suffix screenshot-directory source-expected ".expected")]
                     (io/make-parents ex)
                     (if (.exists source-expected)
                       (do (io/copy source-expected ex)
                           ex)
                       source-expected))
          actual (screenshot-target session target opts)
          {:keys [metric errors] :as result} (compare-images expected actual target opts)
          passed? (< metric metric-threshold)
          result (merge result
                        {:passed? passed?
                         :target target})]

      (log/info "Test result" result)

      (when (not-empty errors)
        (log/error (format "Errors occurred testing %s:" url))
        (doseq [error errors]
          (log/error error)))

      (is passed?
          (format "%s has diverged from reference by %s, please compare \nExpected: %s \nActual: %s \nDifference: %s"
                  reference-file
                  metric
                  (or (:expected-normalised result) (:expected result))
                  (or (:actual-normalised result) (:actual result))
                  (:difference result)))

      result)))

(def default-opts
  {:default-target      {;; :url must be supplied on each target
                         ;; :reference-file must be supplied on each target
                         :metric               "mae" ;; see https://imagemagick.org/script/command-line-options.php#metric
                         :metric-threshold     0.01
                         :load-timeout         60000
                         :reference-directory  "test-resources/kamera"
                         :screenshot-directory "target/kamera"
                         :normalisations       [:trim :crop]
                         :ready? nil ;; (fn [session] ... ) a predicate that should return true when ready to take the screenshot
                                     ;; see element-exists?
                         }
   :normalisation-fns   {:trim trim-images
                         :crop crop-images}
   :imagemagick-options {:path nil      ;; directory where binaries reside on linux, or executable on windows
                         :timeout 2000} ;; kill imagemagick calls that exceed this time, in ms
   :chrome-options      dcd/default-options ;; suggest you fix the width/height to make it device independant
   :report              {:enabled? true ;; write a report after testing
                         }
   })

(defn run-test
  ([target opts]
   (dcd/with-chrome-session
     (:chrome-options opts)
     (fn [session _]
       (run-test session target opts))))
  ([session target opts]
   (let [default-target (:default-target opts)]
     (test-target session (merge default-target target) opts))))

(defn run-tests
  ([targets opts]
   (dcd/with-chrome-session
     (:chrome-options opts)
     (fn [session _]
       (run-tests session targets opts))))
  ([session targets opts]
   (let [results (mapv (fn [target] (run-test session target opts))
                       targets)]
     (when (get-in opts [:report :enabled?])
       (report/write-report! results opts))
     results)))
