(ns kamera.core
  (:require [kamera.report :as report]
            [me.raynes.conch.low-level :as sh]
            [clojure.java.io :as io]
            [clojure.test :refer [testing is]]
            [clj-chrome-devtools.core :as cdp-core]
            [clj-chrome-devtools.impl.connection :as cdp-connection]
            [clj-chrome-devtools.automation :as cdp-automation]
            [clj-chrome-devtools.automation.fixture :as cdp-fixture]
            [clj-chrome-devtools.automation.launcher :as cdp-launcher]
            [clj-chrome-devtools.commands.page :as page]
            [clj-chrome-devtools.commands.dom :as dom]
            [clj-chrome-devtools.commands.emulation :as emulation]
            [clj-chrome-devtools.commands.browser :as browser]
            [clojure.string :as string]
            [clojure.tools.logging :as log])
  (:import [java.io File]
           [java.util Base64]))

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

(defn ->absolute-paths [normalisation-chain]
  (map (fn [n]
         (-> n
             (update :expected #(.getAbsolutePath %))
             (update :actual #(.getAbsolutePath %))))
       normalisation-chain))

(defn compare-images [^File expected
                      ^File actual
                      {:keys [metric screenshot-directory normalisations]}
                      opts]
  (if-not (and (.exists expected) (.exists actual))
    {:metric 1
     :expected (.getAbsolutePath expected)
     :actual (.getAbsolutePath actual)
     :normalisation-chain (->absolute-paths (normalisation-chain expected actual))
     :errors (->> [(when-not (.exists expected) (format "Expected is missing: %s" (.getAbsolutePath expected)))
                   (when-not (.exists actual) (format "Actual is missing: %s" (.getAbsolutePath actual)))]
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
                    :normalisation-chain (->absolute-paths normalisation-chain)}

                   (if (not= 2 exit-code)
                     {:difference (.getAbsolutePath difference)}
                     {:errors [(format "Error comparing images - ImageMagick exited with code %s \n stdout: %s \n stderr: %s"
                                       exit-code stdout stderr)]})

                   (if mean-absolute-error
                     {:metric mean-absolute-error}
                     {:errors [(format "Could not parse ImageMagick output\n stdout: %s \n stderr: %s"
                                       stdout stderr)]}))))))

(defn- body-dimensions [{:keys [connection] :as session}]
  (when-let [body (cdp-automation/sel1 session "body")]
    (:model (dom/get-box-model connection body))))

(defn- browser-dimensions [{:keys [connection]}]
  (select-keys (:bounds (browser/get-window-for-target connection {}))
               [:width :height]))

(defn- resize-window-to-contents! [{:keys [connection] :as session} {:keys [width? height? dom-selector]}]
  (let [width (cdp-automation/evaluate
               session
               (format "document.querySelector(\"%s\").offsetWidth;" dom-selector))
        height (cdp-automation/evaluate
                session
                (format "document.querySelector(\"%s\").offsetHeight;" dom-selector))
        dimensions (cond-> (browser-dimensions session)
                     width? (assoc :width width)
                     height? (assoc :height height))]
    (emulation/set-visible-size connection dimensions)
    (emulation/set-device-metrics-override connection (merge dimensions
                                                             {:device-scale-factor 1.0
                                                              :mobile false}))
    (emulation/set-page-scale-factor connection {:page-scale-factor 1.0})))

(defn- take-screenshot [session {:keys [reference-file screenshot-directory resize-to-contents]} opts]
  (when (and resize-to-contents (some resize-to-contents [:height? :width?]))
    (resize-window-to-contents! session resize-to-contents))

  (let [{:keys [data]} (page/capture-screenshot (:connection session) {:from-surface true})
        file (append-suffix screenshot-directory (io/file reference-file) ".actual")]
    (if data
      (do (io/make-parents file)
          (-> (.decode (Base64/getDecoder) data)
              io/input-stream
              (io/copy file))
          file)
      (log/warn "Got no data from the screenshot for" reference-file))))

(defn element-exists? [selector]
  (fn [session]
    (try (cdp-automation/sel1 session selector)
         (catch Exception _
           false))))

(defn wait-for [session pred-fn]
  (cdp-automation/wait :element false (pred-fn session)))

(defn- screenshot-target [session {:keys [url ready?] :as target} opts]
  (cdp-automation/to session "about:blank") ;; solves a weird bug navigating directly between fragment urls, i think
  (cdp-automation/to session url)

  (when ready?
    (wait-for session ready?))

  (Thread/sleep 500) ;; small timeout for any js rendering

  (take-screenshot session target opts))

(defn test-target [session {:keys [url reference-directory reference-file screenshot-directory metric-threshold assert?] :as target} opts]
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

      (when assert?
        (is passed?
            (format "%s has diverged from reference by %s, please compare \nExpected: %s \nActual: %s \nDifference: %s"
                    reference-file
                    metric
                    (or (:expected-normalised result) (:expected result))
                    (or (:actual-normalised result) (:actual result))
                    (:difference result))))

      result)))

(def default-opts
  {:default-target      {;; :url must be supplied on each target
                         ;; :reference-file must be supplied on each target
                         :metric               "mae" ;; see https://imagemagick.org/script/command-line-options.php#metric
                         :metric-threshold     0.01
                         :reference-directory  "test-resources/kamera"
                         :screenshot-directory "target/kamera"
                         :normalisations       [:trim :crop]
                         :ready?               nil ;; (fn [session] ... ) a predicate that should return true when ready to take the screenshot
                                                   ;; see element-exists?
                         :assert?              true ;; runs a clojure.test assert on the expected/actual when true, makes no assertions when false
                         :resize-to-contents   {:height? true
                                                :width? false
                                                :dom-selector "body"}}
   :normalisation-fns   {:trim trim-images
                         :crop crop-images}
   :imagemagick-options {:path nil      ;; directory where binaries reside on linux, or executable on windows
                         :timeout 2000} ;; kill imagemagick calls that exceed this time, in ms
   :chrome-options      (merge (cdp-launcher/default-options)
                               {:idle-timeout 0
                                :max-msg-size-mb (* 5 1024 1024)
                                :extra-chrome-args ["--window-size=1600,900"
                                                    "--hide-scrollbars"]})
   ;; suggest you fix the width/height to make it device independant
   :report              {:enabled? true ;; write a report after testing
                         }
   })

;; copied from https://github.com/tatut/clj-chrome-devtools/blob/master/src/clj_chrome_devtools/automation/launcher.clj#L39
;; in order to allow injection of extra command line args
(defn launch-chrome [binary-path remote-debugging-port options]
  (log/trace "Launching Chrome headless, binary: " binary-path
             ", remote debugging port: " remote-debugging-port
             ", options: " (pr-str options))
  (let [args (remove nil?
                     (into [binary-path
                            (when (:headless? options) "--headless")
                            (when (:no-sandbox? options) "--no-sandbox")
                            "--disable-gpu"
                            (str "--remote-debugging-port=" remote-debugging-port)]
                           (:extra-chrome-args options)))]
    (.exec (Runtime/getRuntime)
           ^"[Ljava.lang.String;" (into-array String args))))

(defn with-chrome-session [opts f]
  (let [make-ws-delegate cdp-connection/make-ws-client]
    (with-redefs [cdp-connection/make-ws-client #(make-ws-delegate opts)
                  cdp-launcher/launch-chrome launch-chrome]
      ((cdp-fixture/create-chrome-fixture opts)
       #(let [{:keys [connection] :as automation} @cdp-automation/current-automation]
          (cdp-core/set-current-connection! connection)
          (f automation))))))

(defn deep-merge-with
  "Recursively merges maps. Applies function f when we have duplicate keys."
  [f & maps]
  (letfn [(m [& xs]
            (if (some #(and (map? %) (not (record? %))) xs)
              (apply merge-with m xs)
              (f xs)))]
    (reduce m maps)))

(def deep-merge (partial deep-merge-with last))

(defn run-test
  ([target opts]
   (with-chrome-session
     (:chrome-options opts)
     (fn [session]
       (run-test session target opts))))
  ([session target opts]
   (let [default-target (:default-target opts)]
     (test-target session (deep-merge default-target target) opts))))

(defn run-tests
  ([targets opts]
   (with-chrome-session
     (:chrome-options opts)
     (fn [session]
       (run-tests session targets opts))))
  ([session targets opts]
   (let [results (mapv (fn [target] (run-test session target opts))
                       targets)]
     (is (pos? (count results)) "Expected at least one test")
     (when (get-in opts [:report :enabled?])
       (report/write-report! results opts))
     results)))
