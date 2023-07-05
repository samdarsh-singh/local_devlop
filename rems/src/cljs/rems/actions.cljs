(ns rems.actions
  "The /actions page that shows a list of applications you can act on."
  (:require [cljs-time.core :as time-core]
            [re-frame.core :as rf]
            [rems.application-list :as application-list]
            [rems.atoms :refer [document-title]]
            [rems.collapsible :as collapsible]
            [rems.fetcher :as fetcher]
            [rems.flash-message :as flash-message]
            [rems.profile :as profile]
            [rems.search :as search]
            [rems.text :refer [text]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]} _]
   {:db (dissoc db
                ::todo-applications
                ::handled-applications
                ::user-settings)
    :dispatch-n [[::todo-applications]
                 [::user-settings]
                 [:rems.table/reset]]}))

(fetcher/reg-fetcher ::user-settings "/api/user-settings") ; for the EGA warning
(fetcher/reg-fetcher ::todo-applications "/api/applications/todo")
(fetcher/reg-fetcher ::handled-applications "/api/applications/handled")

;;;; UI

(defn actions-page []
  [:div
   [document-title (text :t.navigation/actions)]
   [flash-message/component :top]
   [profile/maybe-ega-api-key-warning]
   [:div.spaced-sections
    [collapsible/component
     {:id "todo-applications-collapse"
      :open? true
      :title (text :t.actions/todo-applications)
      :collapse [:<>
                 [search/application-search-field {:id "todo-search"
                                                   :on-search #(rf/dispatch [::todo-applications {:query %}])
                                                   :searching? @(rf/subscribe [::todo-applications :searching?])}]
                 [application-list/component {:applications ::todo-applications
                                              :hidden-columns #{:state :created}
                                              :default-sort-column :last-activity
                                              :default-sort-order :desc}]]}]
    [collapsible/component
     {:id "handled-applications-collapse"
      :on-open #(rf/dispatch [::handled-applications])
      :title (text :t.actions/handled-applications)
      :collapse [:<>
                 [search/application-search-field {:id "handled-search"
                                                   :on-search #(rf/dispatch [::handled-applications {:query %}])
                                                   :searching? @(rf/subscribe [::handled-applications :searching?])}]
                 [application-list/component {:applications ::handled-applications
                                              :hidden-columns #{:todo :created :submitted}
                                              :default-sort-column :last-activity
                                              :default-sort-order :desc}]]}]]])
