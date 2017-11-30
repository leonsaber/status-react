(ns status-im.data-store.discover
  (:require [status-im.data-store.realm.discover :as data-store]
            [status-im.utils.handlers :as handlers]))

;; stores a discover message
;; removes the tags from the discovers because there is no
;; need to store them and realm doesn't support lists
;; of string
;; also deletes the oldest queries if the number of discovers stored is
;; above maximum-number-of-discoveries
(re-frame/reg-fx
  ::save
  (fn [[discover maximum-number-of-discoveries]]
    (data-store/save (dissoc :tags discover))
    (data-store/delete :created-at :asc maximum-number-of-discoveries)))

;; same as save but for many discover messages
(re-frame/reg-fx
  ::save-all
  (fn [[discovers maximum-number-of-discoveries]]
    (data-store/save-all (mapv #(dissoc :tags %) discovers))
    (data-store/delete :created-at :asc maximum-number-of-discoveries)))

;; we extract the hashtags from the status and put them into a set
;; for each discover and return a map of discovers that can be used
;; as is in the app-db
;; also deletes the oldest queries if the number of discovers stored is
;; above maximum-number-of-discoveries
(re-frame/reg-fx
  ::get-all
  (fn [[callback maximum-number-of-discoveries]]
    (data-store/delete :created-at :asc maximum-number-of-discoveries)
    (callback (reduce (fn [acc {:keys [message-id status] :as discover}]
                        (let [tags     (handlers/get-hashtags status)
                              discover (assoc discover :tags tags)])
                        (assoc acc message-id discover))
                      {}
                      (data-store/get-all-as-list :asc)))))
