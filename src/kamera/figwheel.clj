(ns kamera.figwheel
  (:require [kamera.core :as k]
            [figwheel.main.api :as fig-api]
            [figwheel.main :as fig]
            [hickory.core :as h]
            [hickory.select :as s]
            [clojure.string :as string]
            [doo-chrome-devprotocol.core :as dcd]
            [clojure.test :refer [deftest]]
            [clojure.tools.logging :as log]
            [clojure.edn :as edn])
  (:import [io.webfolder.cdp.session Session]))

;; nice figwheel changes:
;; 1. make fig/start-build-arg->build-options public
;; 2. make the host/port easier to get at in a running server, currently hack the ws url
;; 3. ask if there's a better way to get the list of tests rather than scraping
;; 4. ask how to integrate to the point of {:kamera true} like devcards

(def build-arg->build-opts
  #'fig/start-build-arg->build-options)

(defn- build-for [build-or-id {:keys [devcards-path]}]
  (-> (build-arg->build-opts build-or-id)
      (update :config merge
              {:mode :serve
               :open-url false
               :connect-url (format "http://[[config-hostname]]:[[server-port]]/%s" devcards-path)})))

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

(defn find-test-urls [^Session session]
  (let [dom (.getDOM (.getCommand session))
        root-node (.getDocument dom)
        html (.getOuterHTML dom (.getNodeId root-node) nil nil)]
    (extract-links html)))

(def default-opts
  (assoc k/default-opts
         :devcards-path "devcards.html"
         :init-hook nil ;; (fn [session]) function run before attempting to scrape targets
         ))

(defn test-devcards
  ([build-or-id] (test-devcards build-or-id default-opts))

  ([build-or-id opts]
   (dcd/with-chrome-session (:chrome-options opts)
     (fn [session _]
       (test-devcards session build-or-id opts))))

  ([^Session session build-or-id opts]
   (let [devcards-url (start-devcards build-or-id opts)]
     (try
       (test-devcards devcards-url session build-or-id opts)
       (finally
         (stop-devcards build-or-id)))))

  ([devcards-url ^Session session _ {:keys [init-hook] :as opts}]
   (log/info "Navigating to" devcards-url)
   (.navigate session devcards-url)
   (.waitDocumentReady session 15000)
   (Thread/sleep 10000) ;; wait for devcards to load fully
   (when init-hook
     (init-hook session))
   (let [target-urls (find-test-urls session)]
     (k/run-tests
      session
      (map (fn [target-url]
             {:url target-url
              :reference-file (str (subs target-url 3) ".png")})
           target-urls)
      (-> opts
          (update :default-target assoc :root devcards-url))))))

;; maybe it's possible to get chrome to execute a script that calls fighwheel in cljs to get a list of test namespaces rather than scraping?
