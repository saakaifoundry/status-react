(ns status-im.debug.events
  (:require [clojure.string :as string]
            [re-frame.core :as re-frame]
            [status-im.data-store.accounts :as data-store.accounts]
            [status-im.models.network :as models.network]
            [status-im.debug.http-server :as http-server]
            [status-im.utils.handlers :as handlers]
            [taoensso.timbre :as log]))

;; FX

(re-frame/reg-fx
 ::start
 (fn [[address force-start?]]
   (if force-start?
     (http-server/start!)
     (let [{:keys [debug?]} (data-store.accounts/get-by-address address)]
       (when debug?
         (http-server/start!))))))

(re-frame/reg-fx
 ::stop
 (fn []
   (http-server/stop!)))

(re-frame/reg-fx
 ::respond
 (fn [[status-code data]]
   (http-server/respond! status-code data)))

;; Specific server operations

(defmulti process-request! (fn [{:keys [url type]}] [(keyword type) (first url)]))

(defmethod process-request! [:POST "ping"]
  [_]
  {::respond [200 {:message "Pong!"}]})

(defmethod process-request! [:POST "open-dapp"]
  [{{:keys [url]} :data}]
  {:dispatch [:open-url-in-browser url]
   ::respond [200 {:message "URL has been opened."}]})

(defmethod process-request! [:POST "network"]
  [{:keys [cofx data]}]
  (let [data (->> data
                  (map (fn [[k v]] [k {:value v}]))
                  (into {}))]
    (models.network/save cofx
                         {:data       data
                          :on-success (fn [network]
                                        {::respond [200 {:message    "Network has been added."
                                                         :network-id network}]})
                          :on-failure (fn []
                                        {::respond [400 {:message "Please, check the validity of network."}]})})))

(defmethod process-request! [:DELETE "network"]
  [{{:keys [node]} :data}]
  {:dispatch [:delete-network node]
   ::respond [200 {:message "Network has been changed."}]})

(defmethod process-request! [:POST "connect-network"]
  [{{:keys [node]} :data}]
  {:dispatch [:connect-network node]
   ::respond [200 {:message "Network has been changed."}]})

(defmethod process-request! :default
  [{:keys [url]}]
  {::respond [404 {:message (str "Not found (url: " url ")")}]})

;; Handlers

(handlers/register-handler-fx
 :start-http-server
 [re-frame/trim-v]
 (fn [_ [{:keys [address force-start?]}]]
   {::start [address force-start?]}))

(handlers/register-handler-fx
 :stop-debug-server
 (fn [_]
   {::stop nil}))

(handlers/register-handler-fx
 :process-http-request
 [re-frame/trim-v (re-frame/inject-cofx :random-id)]
 (fn [cofx [url type data]]
   (try
     (process-request! {:cofx cofx
                        :url  (rest (string/split url "/"))
                        :type type
                        :data data})
     (catch js/Error e
       {::respond [400 {:message (str "Unsupported operation: " e)}]}))))