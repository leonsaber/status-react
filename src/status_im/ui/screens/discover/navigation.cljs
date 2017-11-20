(ns status-im.ui.screens.discover.navigation
  (:require [status-im.ui.screens.navigation :as navigation]
            [status-im.data-store.discover :as discoveries]))

(defmethod navigation/preload-data! :discover
  [db _]
  (-> db
      (assoc-in [:toolbar-search :show] nil)
      (assoc :tags (discoveries/get-all-tags)
             :discoveries (->> (discoveries/get-all :desc)
                               (map (fn [{:keys [message-id] :as discover}]
                                      [message-id discover]))
                               (into {})))))
