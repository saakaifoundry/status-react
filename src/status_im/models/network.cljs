(ns status-im.models.network
  (:require [clojure.string :as string]
            [status-im.utils.ethereum.core :as ethereum]
            [status-im.utils.handlers-macro :as handlers-macro]
            [status-im.ui.screens.accounts.utils :as accounts.utils]))

(def url-regex
  #"https?://(www\.)?[-a-zA-Z0-9@:%._\+~#=]{2,256}(\.[a-z]{2,6})?\b([-a-zA-Z0-9@:%_\+.~#?&//=]*)")

(defn valid-rpc-url? [url]
  (boolean (re-matches url-regex (str url))))

(def default-manage
  {:name  {:value ""}
   :url   {:value ""}
   :chain {:value :mainnet}})

(defn validate-string [{:keys [value]}]
  {:value value
   :error (string/blank? value)})

(defn validate-url [{:keys [value]}]
  {:value value
   :error (not (valid-rpc-url? value))})

(defn validate-manage [manage]
  (-> manage
      (update :url validate-url)
      (update :name validate-string)
      (update :chain validate-string)))

(defn valid-manage? [manage]
  (->> (validate-manage manage)
       vals
       (map :error)
       (not-any? identity)))

(defn new-network [random-id network-name upstream-url type network-id]
  (let [data-dir (str "/ethereum/" (name type) "_rpc")
        config   {:NetworkId      (or (when network-id (int network-id))
                                      (ethereum/chain-keyword->chain-id type))
                  :DataDir        data-dir
                  :UpstreamConfig {:Enabled true
                                   :URL     upstream-url}}]
    {:id         (string/replace random-id "-" "")
     :name       network-name
     :config     config}))

(defn get-chain [{:keys [db]}]
  (let [network  (get (:networks (:account/account db)) (:network db))]
    (ethereum/network->chain-keyword network)))

(defn set-input [input-key value {:keys [db]}]
  {:db (-> db
           (update-in [:networks/manage input-key] assoc :value value)
           (update-in [:networks/manage] validate-manage))})

(defn- save-on-success [on-success network-id cofx]
  (when on-success
    (on-success network-id)))

(defn save
  ([cofx]
   (save cofx nil))
  ([{{:network/keys [manage]
      :account/keys [account] :as db} :db :as cofx}
    {:keys [data on-success on-failure]}]
   (let [data (or data manage)]
     (if (valid-manage? data)
       (let [{:keys [name url chain network-id]} data
             network      (new-network (:random-id cofx) (:value name) (:value url) (:value chain) (:value network-id))
             new-networks (merge {(:id network) network} (:networks account))]
         (handlers-macro/merge-fx cofx
                                  {:db (dissoc db :networks/manage)}
                                  (save-on-success on-success (:id network))
                                  (accounts.utils/account-update {:networks new-networks})))
       (when on-failure
         (on-failure))))))

;; No edit functionality actually implemented
(defn edit [{db :db}]
  {:db       (assoc db :networks/manage (validate-manage default-manage))
   :dispatch [:navigate-to :edit-network]})
