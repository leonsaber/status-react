(ns status-im.ui.screens.discover.events
  (:require [re-frame.core :as re-frame]
            [status-im.data-store.discover :as data-store.discover]
            [status-im.protocol.core :as protocol]
            [status-im.ui.screens.discover.navigation]
            [status-im.utils.handlers :as handlers]))

(def request-discoveries-interval-s 600)
(def maximum-number-of-discoveries 1000)

;; EFFECTS

(re-frame/reg-fx
  ::send-portions
  (fn [{:keys [current-public-key web3 contacts to discoveries]}]
    (protocol/send-discoveries-response!
     {:web3        web3
      :discoveries discoveries
      :message     {:from current-public-key
                    :to   to}})))

(re-frame/reg-fx
  ::request-discoveries
  (fn [{:keys [identities web3 current-public-key message-id]}]
    (doseq [id identities]
      (when-not (protocol/message-pending? web3 :discoveries-request id)
        (protocol/send-discoveries-request!
         {:web3    web3
          :message {:from       current-public-key
                    :to         id
                    :message-id message-id}})))))

(re-frame/reg-fx
  ::broadcast-status
  (fn [{:keys [identities web3 message]}]
    (doseq [id identities]
      (protocol/send-status!
       {:web3    web3
        :message (assoc message :to id)}))))

;; HELPER-FN

(defn send-portions-when-contact-exists
  [{:keys [current-public-key web3]
    :contacts/keys [contacts]}
   to]
  (when (get contacts to)
    {::send-portions {:current-public-key current-public-key
                      :web3               web3
                      :contacts           contacts
                      :to                 to}}))

(defn add-discover [db {:keys [message-id] :as discover}]
  (assoc-in db [:discoveries message-id] discover))

(defn add-discovers [db discovers]
  (reduce add-discover db discovers))

(defn new-discover? [discoveries {:keys [message-id]}]
  (not (get discoveries message-id )))


;; EVENTS

;; TODO(goranjovic): at the moment we do nothing when a status without hashtags is posted
;; but we probably should post a special "delete" status that removes any previous
;; hashtag statuses in that scenario. In any case, that's the reason why this event
;; gets even the statuses without a hashtag - it may need to do stuff with them as well.
(handlers/register-handler-fx
  :broadcast-status
  [(re-frame/inject-cofx :random-id)]
  (fn [{{:keys [current-public-key web3]
         :accounts/keys [accounts current-account-id]
         :contacts/keys [contacts]} :db
        random-id :random-id}
       [_ status]]
    (when-let [hashtags (seq (handlers/get-hashtags status))]
      (let [{:keys [name photo-path]} (get accounts current-account-id)
            message    {:message-id random-id
                        :from       current-public-key
                        :payload    {:message-id random-id
                                     :status     status
                                     :hashtags   (vec hashtags)
                                     :profile    {:name          name
                                                  :profile-image photo-path}}}]

        {::broadcast-status {:web3       web3
                             :message    message
                             :identities (handlers/identities contacts)}
         :dispatch          [:status-received message]}))))

(handlers/register-handler-fx
  :init-discoveries
  (fn [_ _]
    {::data-store.discover/get-all [#(re-frame/dispatch [:data-store/db-update {:discoveries %}])
                                    maximum-number-of-discoveries]
     :dispatch                     [:request-discoveries]}))

(handlers/register-handler-fx
  :request-discoveries
  [(re-frame/inject-cofx :random-id)]
  (fn [{{:keys [current-public-key web3]
         :contacts/keys [contacts]} :db
        random-id :random-id} _]
    ;; this event calls itself at regular intervals
    ;; TODO (yenda): this was previously using setInterval explicitly, with
    ;; dispatch-later it is using it implicitly. setInterval is
    ;; problematic for such long period of time and will cause a warning
    ;; for Android in latest versions of react-nativexb
    {::request-discoveries {:current-public-key current-public-key
                            :web3               web3
                            :identities         (handlers/identities contacts)
                            :message-id         random-id}
     :dispatch-later       [{:ms       (* request-discoveries-interval-s 1000)
                             :dispatch [:request-discoveries]}]}))

(handlers/register-handler-fx
  :discoveries-send-portions
  (fn [{:keys [db]} [_ to]]
    (send-portions-when-contact-exists db to)))

(handlers/register-handler-fx
  :discoveries-request-received
  (fn [{:keys [db]} [_ {:keys [from]}]]
    (send-portions-when-contact-exists db from)))

(handlers/register-handler-fx
  :discoveries-response-received
  [(re-frame/inject-cofx :now)]
  (fn [{{:keys [discoveries]
         :contacts/keys [contacts] :as db} :db
        now :now}
       [_ {:keys [payload from]}]]
    (when (get contacts from)
      (when-let [discovers (some->> (:data payload)
                                    (filter #(new-discover? discoveries %))
                                    (map #(assoc % :created-at now)))]
        {:db                            (add-discovers db discovers)
         ::data-store.discover/save-all [discovers maximum-number-of-discoveries]}))))

(handlers/register-handler-fx
  :status-received
  [(re-frame/inject-cofx :now)]
  (fn [{{:keys [discoveries] :as db} :db
        now :now}
       [_ {{:keys [message-id status profile] :as payload} :payload
           from :from}]]
    (when (new-discover? discoveries payload)
      (let [{:keys [name profile-image]} profile
            discover {:message-id message-id
                      :name       name
                      :photo-path profile-image
                      :status     status
                      :whisper-id from
                      :created-at now}]
        {:db                        (add-discover db (assoc discover :tags (handlers/get-hashtags status)))
         ::data-store.discover/save [discover maximum-number-of-discoveries]}))))
