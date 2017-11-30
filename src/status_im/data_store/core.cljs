(ns status-im.data-store.core
  (:require [status-im.data-store.realm.core :as data-source]
            [status-im.utils.handlers :as handlers]))


(defn init []
  (data-source/reset-account))

(defn change-account [address new-account? handler]
  (data-source/change-account address new-account? handler))

;; This is a naive db-update handler for simple callbacks where
;; the result of a query can be merged to the app-db
(handlers/register-handler-fx
  :data-store/db-update
  (fn [db-update]
    (merge db db-update)))
