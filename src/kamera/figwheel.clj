(ns kamera.figwheel
  (:require [kamera.core :as k]
            [figwheel.main.api :as fig-api]
            [figwheel.main :as fig]
            [hickory.core :as h]
            [hickory.select :as s]
            [clojure.string :as string]
            [doo-chrome-devprotocol.core :as dcd]
            [clojure.test :refer [deftest]]
            [clojure.tools.logging :as log])
  (:import [io.webfolder.cdp.session Session]))

(defn start-devcards [build-id {:keys [devcards-path]}]
  (log/info "Starting figwheel" build-id)
  (fig-api/start {:mode :serve
                  :open-url false
                  :connect-url (format "http://[[config-hostname]]:[[server-port]]/%s" devcards-path)}
                 build-id)
  ;; looks like you have to look at the websocket url to know what the port and hostname are going to be, bit rubbish
  (let [config (fig/config-for-id build-id)]
    (try
      (assert (get-in config [:options :devcards]) "Devcards must be enabled")
      (let [connect-url (get-in config [:options :closure-defines 'figwheel.repl/connect-url])]
        (assert connect-url "Could not detect a url to connect to")
        connect-url)
      (catch Exception e
        (fig-api/stop build-id)
        nil))))

(defn stop-devcards [build-id]
  (fig-api/stop build-id))

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
  ([build-id] (test-devcards build-id default-opts))

  ([build-id opts]
   (dcd/with-chrome-session (:chrome-options opts)
     (fn [session _]
       (test-devcards session build-id opts))))

  ([^Session session build-id opts]
   (let [devcards-url (start-devcards build-id opts)]
     (try
       (test-devcards devcards-url session build-id opts)
       (finally
         (stop-devcards build-id)))))

  ([devcards-url ^Session session build-id {:keys [init-hook] :as opts}]
   (.navigate session devcards-url)
   (.waitDocumentReady session 15000)
   (Thread/sleep 2000)
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
