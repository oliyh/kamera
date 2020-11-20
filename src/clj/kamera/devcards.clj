(ns kamera.devcards
  (:require [kamera.core :as k]
            [figwheel.main.api :as fig-api]
            [figwheel.main :as fig]
            [hickory.core :as h]
            [hickory.select :as s]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [clj-chrome-devtools.automation :as cdp-automation]
            [clj-chrome-devtools.commands.dom :as dom]))

;; nice figwheel changes:
;; 1. make fig/start-build-arg->build-options public
;; 2. make the host/port easier to get at in a running server, currently hack the ws url
;; 3. ask if there's a better way to get the list of tests rather than scraping
;; 4. ask how to integrate to the point of {:kamera true} like devcards

(def build-arg->build-opts
  #'fig/start-build-arg->build-options)

(defn- build-for [build-or-id {:keys [devcards-options]}]
  (-> (build-arg->build-opts build-or-id)
      (update :config merge
              {:mode :serve
               :open-url false
               :connect-url (format "http://[[config-hostname]]:[[server-port]]/%s"
                                    (:path devcards-options))})))

(defn start-devcards [build-or-id opts]
  (let [build (build-for build-or-id opts)
        build-id (:id build)]
    (log/info "Starting figwheel" build-id)
    (fig-api/start build)
    ;; looks like you have to look at the websocket url to know what the port and hostname are going to be, bit rubbish
    (let [config (fig/config-for-id build-id)]
      (try
        (assert (get-in config [:options :devcards]) "Devcards must be enabled")
        (let [connect-url (get-in config [:options :closure-defines 'figwheel.repl/connect-url])]
          (assert connect-url "Could not detect a url to connect to")
          connect-url)
        (catch Exception e
          (fig-api/stop build-id)
          nil)))))

(defn stop-devcards [build-or-id]
  (fig-api/stop (if (map? build-or-id)
                  (:id build-or-id)
                  build-or-id)))

(defn extract-links [content]
  (->> (h/as-hickory (h/parse content))
       (s/select (s/child (s/class "com-rigsomelight-devcards-list-group-item")
                          s/last-child))
       (map (comp first :content))
       (map string/trim)
       (map #(str "#!/" %))))

(defn find-test-urls [{:keys [connection] :as session}]
  (extract-links (:outer-html (dom/get-outer-html connection (cdp-automation/root session)))))

(def devcards-list-ready?
  (k/element-exists? ".com-rigsomelight-devcards-list-group"))

(def devcards-page-ready?
  (k/element-exists? ".com-rigsomelight-devcard"))

(def default-opts
  (-> k/default-opts
      (assoc :devcards-options
             {:path "devcards.html" ;; the relative path to the page where the devcards are hosted
              :init-hook nil ;; (fn [session]) function run before attempting to scrape targets
              :on-targets nil ;; (fn [targets]) function called to allow changing the targets before the test is run
              :timeout 60000  ;; time to wait for any devcards page to load
              })
      ;; wait for devcards div to appear before taking screenshot
      (assoc-in [:default-target :ready?] devcards-page-ready?)))

(defn test-devcards
  ([build-or-id] (test-devcards build-or-id default-opts))
  ([build-or-id opts]
   (k/with-chrome-session (:chrome-options opts)
     (fn [session]
       (test-devcards session build-or-id opts))))

  ([session build-or-id opts]
   (let [devcards-url (start-devcards build-or-id opts)]
     (try
       (test-devcards devcards-url session build-or-id opts)
       (finally
         (stop-devcards build-or-id)))))

  ([devcards-url session _ {:keys [devcards-options] :as opts}]
   (let [{:keys [init-hook on-targets]} devcards-options]
     (log/info "Navigating to" devcards-url)
     (cdp-automation/to session devcards-url)
     (k/wait-for session devcards-list-ready?)
     (Thread/sleep 500)
     (when init-hook
       (init-hook session))
     (let [target-urls (find-test-urls session)
           targets (map (fn [target-url]
                          {:url (str devcards-url target-url)
                           :resize-to-contents {:dom-selector "#com-rigsomelight-devcards-main"}
                           :reference-file (str (subs target-url 3) ".png")})
                        target-urls)
           targets (if on-targets
                     (on-targets targets)
                     targets)]
       (log/infof "Found %s devcards to test" (count target-urls))
       (k/run-tests session targets opts)))))
