(ns rems.application
  (:require [clojure.string :as str]
            [clojure.set :refer [union]]
            [goog.string]
            [re-frame.core :as rf]
            [medley.core :refer [assoc-some find-first filter-keys filter-vals update-existing]]
            [cljs-time.core :as time-core]
            [rems.actions.accept-licenses :refer [accept-licenses-action-button]]
            [rems.actions.components :refer [button-wrapper]]
            [rems.actions.add-licenses :refer [add-licenses-action-button add-licenses-form]]
            [rems.actions.add-member :refer [add-member-action-button add-member-form]]
            [rems.actions.approve-reject :refer [approve-reject-action-button approve-reject-form]]
            [rems.actions.assign-external-id :refer [assign-external-id-button assign-external-id-form]]
            [rems.actions.change-applicant :refer [change-applicant-action-button change-applicant-form]]
            [rems.actions.change-resources :refer [change-resources-action-button change-resources-form]]
            [rems.actions.close :refer [close-action-button close-form]]
            [rems.actions.decide :refer [decide-action-button decide-form]]
            [rems.actions.delete :refer [delete-action-button delete-form]]
            [rems.actions.invite-decider-reviewer :refer [invite-decider-action-link invite-reviewer-action-link invite-decider-form invite-reviewer-form]]
            [rems.actions.invite-member :refer [invite-member-action-button invite-member-form]]
            [rems.actions.redact-attachments :refer [redact-attachments-action-button redact-attachments-form]]
            [rems.actions.remark :refer [remark-action-button remark-form]]
            [rems.actions.remove-member :refer [remove-member-action-button remove-member-form]]
            [rems.actions.request-decision :refer [request-decision-action-link request-decision-form]]
            [rems.actions.request-review :refer [request-review-action-link request-review-form]]
            [rems.actions.return-action :refer [return-action-button return-form]]
            [rems.actions.review :refer [review-action-button review-form]]
            [rems.actions.revoke :refer [revoke-action-button revoke-form]]
            [rems.application-list :as application-list]
            [rems.administration.duo :refer [duo-field duo-info-field]]
            [rems.common.application-util :as application-util :refer [accepted-licenses? form-fields-editable? get-member-name is-handler?]]
            [rems.common.attachment-util :as attachment-util]
            [rems.atoms :refer [external-link expander file-download info-field readonly-checkbox document-title success-symbol make-empty-symbol]]
            [rems.common.catalogue-util :refer [catalogue-item-more-info-url]]
            [rems.collapsible :as collapsible]
            [rems.common.form :as form]
            [rems.common.util :refer [build-index index-by parse-int]]
            [rems.common.duo :refer [duo-validation-summary unmatched-duos]]
            [rems.dropdown :as dropdown]
            [rems.fetcher :as fetcher]
            [rems.fields :as fields]
            [rems.focus :as focus]
            [rems.flash-message :as flash-message]
            [rems.guide-util :refer [component-info example lipsum lipsum-paragraphs]]
            [rems.phase :refer [phases]]
            [rems.spinner :as spinner]
            [rems.text :refer [localize-decision localize-event localized localize-state localize-time localize-time-with-seconds text text-format]]
            [rems.user :as user]
            [rems.util :refer [navigate! fetch post! focus-input-field focus-when-collapse-opened format-file-size]]))

;;;; Helpers

(defn reload! [application-id & [full-reload?]]
  (rf/dispatch [::fetch-application application-id full-reload?]))

(defn- disabled-items-warning [application]
  (when (contains? (:application/permissions application) :see-everything) ; don't show to applicants
    (when-some [resources (->> (:application/resources application)
                               (filter #(or (not (:catalogue-item/enabled %))
                                            (:catalogue-item/expired %)
                                            (:catalogue-item/archived %)))
                               seq)]
      [:div.alert.alert-warning
       (text :t.form/alert-disabled-resources)
       (into [:ul]
             (for [resource resources]
               [:li (localized (:catalogue-item/title resource))]))])))

(defn- blacklist-warning [application]
  (let [resources-by-id (group-by :resource/ext-id (:application/resources application))
        blacklist (:application/blacklist application)]
    (when (seq blacklist)
      [:div.alert.alert-danger
       (text :t.form/alert-blacklisted-users)
       (into [:ul]
             (for [entry blacklist
                   resource (get resources-by-id (get-in entry [:blacklist/resource :resource/ext-id]))]
               [:li (get-member-name (:blacklist/user entry))
                ": " (localized (:catalogue-item/title resource))]))])))

(defn- format-validations [{:keys [fields label validations]}]
  [:div label
   (into [:ul]
         (for [{:keys [type form-id field-id]} validations]
           [:li (if-some [field (get-in fields [form-id field-id])]
                  [:a {:href "#"
                       :on-click (case (:field/type field)
                                   ;; workaround for tables: there's no single input to focus
                                   :table #(focus/focus-selector (str "#container-"
                                                                      (fields/field-name field)))
                                   :attachment (focus-input-field (str "upload-"
                                                                       (fields/field-name field)))
                                   (focus-input-field (fields/field-name field)))}
                   (text-format type (localized (:field/title field)))]
                  (text type))]))])

(defn- validations [{:keys [application warnings errors]}]
  (let [fields-index (index-by [:form/id :field/id]
                               (for [form (:application/forms application)
                                     field (:form/fields form)]
                                 (assoc field :form/id (:form/id form))))]
    [:<>
     (when (seq errors)
       [format-validations {:fields fields-index
                            :label (text :t.actions.errors/validation-errors)
                            :validations errors}])
     (when (seq warnings)
       [format-validations {:fields fields-index
                            :label (text :t.actions.errors/validation-warnings)
                            :validations warnings}])]))

;;;; State

(rf/reg-sub ::application-id (fn [db _] (::application-id db)))
(rf/reg-sub ::application (fn [db [_ k]] (get-in db [::application (or k :data)])))
(rf/reg-sub ::edit-application (fn [db _] (::edit-application db)))
(rf/reg-sub ::readonly? :<- [::application] (comp not form-fields-editable?))

(rf/reg-event-fx
 ::enter-application-page
 (fn [{:keys [db]} [_ id]]
   {:db (-> db
            (assoc ::application-id (parse-int id))
            (dissoc ::application
                    ::edit-application
                    ::duo-codes
                    ::autosaving
                    ::highlight-event-ids))
    :dispatch-n (if (-> db :config :enable-duo)
                  [[::fetch-application id true] [::duo-codes]]
                  [[::fetch-application id true]])}))

(rf/reg-event-fx
 ::fetch-application
 (fn [{:keys [db]} [_ id full-reload?]]
   (fetch (str "/api/applications/" id)
          {:handler #(rf/dispatch [::fetch-application-result % full-reload?])
           :error-handler (comp #(rf/dispatch [::fetch-application-result nil full-reload?])
                                (flash-message/default-error-handler :top [text :t.applications/application]))})
   {:db (update db ::application fetcher/started)}))

(defn- index-duo-restrictions [restrictions]
  (->> restrictions
       (build-index {:keys [:type]
                     :value-fn (fn [restriction]
                                 (case (:type restriction)
                                   :mondo (:values restriction)
                                   (->> (:values restriction)
                                        first
                                        :value)))})))

(defn- initialize-edit-application [db]
  (let [application (get-in db [::application :data])
        field-values (for [form (:application/forms application)
                           field (:form/fields form)]
                       {:form (:form/id form)
                        :field (:field/id field)
                        :value (:field/value field)})
        field-values-by-form-field (->> field-values
                                        (build-index {:keys [:form :field] :value-fn :value}))
        duo-codes (->> (get-in application [:application/duo :duo/codes])
                       (map #(update-existing % :restrictions index-duo-restrictions))
                       (build-index {:keys [:id]}))
        any-field-answer? (some (fn [{:keys [value]}]
                                  (cond (string? value) (not (str/blank? value))
                                        (coll? value) (seq value)
                                        :else (some? value)))
                                field-values)
        any-duo-answer? (seq duo-codes)]
    (when (and (form-fields-editable? application)
               (or any-field-answer? any-duo-answer?))
      (rf/dispatch [::validate-application]))
    (assoc db ::edit-application {:duo-codes duo-codes
                                  :field-values field-values-by-form-field
                                  :show-diff {}
                                  :validation nil
                                  :attachment-status {}})))

(defn- set-highlightables-by-request-id [events]
  (let [related-events-by-request-id (->> events
                                          (group-by :application/request-id)
                                          (filter-keys some?)
                                          (filter-vals #(<= 2 (count %))))]
    (for [event events
          :let [highlightable-event-ids (->> event
                                             :application/request-id
                                             (get related-events-by-request-id)
                                             (into #{} (map :event/id)))]]
      (update event :highlight-event-ids into highlightable-event-ids))))

(defn- set-highlightables-by-redacted-attachments [events attachments]
  (let [attachments (build-index {:keys [:attachment/id]} attachments)
        related-events (for [event events
                             id (map :attachment/id (:event/redacted-attachments event))
                             :let [added-in-event-id (get-in attachments [id :attachment/event :event/id])]]
                         #{added-in-event-id (:event/id event)})]
    (for [event events
          :let [highlightable-event-ids (->> related-events
                                             (filter #(contains? % (:event/id event)))
                                             (reduce into #{}))]]
      (update event :highlight-event-ids into highlightable-event-ids))))

(defn- set-application-event-highlightables
  "Updates events with event ids that are related to the event so that they can
   be highlighted within the UI."
  [application]
  (-> application
      (update :application/events set-highlightables-by-request-id)
      (update :application/events set-highlightables-by-redacted-attachments (:application/attachments application))))

(rf/reg-event-fx
 ::fetch-application-result
 (fn [{:keys [db]} [_ application full-reload?]]
   (let [initial-fetch? (not (:initialized? (::application db)))]
     {:db (-> db
              (update ::application fetcher/finished application)
              (update-in [::application :data] set-application-event-highlightables)
              (cond-> (or initial-fetch? full-reload?) (initialize-edit-application)))})))

(rf/reg-event-db
 ::set-validations
 (fn [db [_ errors warnings]]
   (-> db
       (assoc-in [::edit-application :validation :errors] errors)
       (assoc-in [::edit-application :validation :warnings] warnings))))

(defn- field-values-to-api [application field-values]
  (for [form (:application/forms application)
        :let [form-id (:form/id form)]
        field (:form/fields form)
        :let [field-id (:field/id field)]
        :when (form/field-visible? field (get field-values form-id))]
    {:form form-id :field field-id :value (get-in field-values [form-id field-id])}))

(defn- handle-validations!
  [{:keys [errors warnings success] :as _response}
   description
   application
   & [{:keys [on-success default-success? focus? warn-about-missing?]
       :or {default-success? true
            focus? true
            warn-about-missing? true}}]]
  (let [warnings (if warn-about-missing? warnings (remove (comp #{:t.form.validation/required} :type) warnings))]
    (flash-message/clear-message! :top-validation)
    (flash-message/clear-message! :actions)
    (rf/dispatch [::set-validations errors warnings])
    (if-not success
      (flash-message/show-default-error! :top-validation
                                         description
                                         [validations {:application application
                                                       :errors errors}])
      (do
        (if (seq warnings)
          (flash-message/show-default-warning! :top-validation
                                               description
                                               {:focus? focus?
                                                :content [[validations {:application application
                                                                        :warnings warnings}]]})
          (when default-success? (flash-message/show-default-success! :actions description)))
        (when on-success (on-success))))))

(defn- duo-codes-to-api [duo-codes]
  (for [duo duo-codes]
    {:id (:id duo)
     :restrictions (for [restriction (:restrictions duo)
                         :let [values (val restriction)]]
                     {:type (key restriction)
                      :values (case (key restriction)
                                :mondo (map #(select-keys % [:id]) values)
                                (if (some? values) [{:value values}] []))})}))

(rf/reg-event-fx
 ::validate-application
 (fn [{:keys [db]} [_]]
   (let [application (:data (::application db))
         edit-application (::edit-application db)
         description [text :t.applications/continue-existing-application]]
     (flash-message/clear-message! :actions)
     (post! "/api/applications/validate"
            {:params {:application-id (:application/id application)
                      :field-values (field-values-to-api application (:field-values edit-application))
                      :duo-codes (duo-codes-to-api (vals (:duo-codes edit-application)))}
             :handler (fn [response]
                        (handle-validations! (dissoc response :errors) ; only use the warnings in this step
                                             description application {:default-success? false
                                                                      :warn-about-missing? false})) ; don't complain about unfilled required fields
             :error-handler (flash-message/default-error-handler :actions description)})
     {})))

(defn- save-draft! [description application edit-application handler & [{:keys [error-handler]}]]
  (flash-message/clear-message! :actions)
  (post! "/api/applications/save-draft"
         {:params {:application-id (:application/id application)
                   :field-values (field-values-to-api application (:field-values edit-application))
                   :duo-codes (duo-codes-to-api (vals (:duo-codes edit-application)))}
          :handler handler
          :error-handler (or error-handler
                             (flash-message/default-error-handler :actions description))}))

(rf/reg-event-fx
 ::save-application
 (fn [{:keys [db]} [_ description on-success]]
   (let [application (:data (::application db))
         edit-application (::edit-application db)]
     (save-draft! description
                  application
                  edit-application
                  (fn [response]
                    (rf/dispatch [::fetch-application (:application/id application)])
                    (handle-validations! response description application {:on-success on-success}))))
   {:db (-> db
            (assoc-in [::edit-application :validation :errors] nil)
            (assoc-in [::edit-application :validation :warnings] nil))}))

(rf/reg-event-db ::set-autosaving (fn [db [_ value]] (assoc db ::autosaving value)))
(rf/reg-sub ::autosaving (fn [db _] (::autosaving db)))

(rf/reg-event-fx
 ::autosave-application
 (fn [{:keys [db]} [_]]
   (if (-> db :config :enable-autosave)
     (let [application (-> db ::application :data)
           edit-application (::edit-application db)
           description [text :t.form/autosave]]
       (save-draft! description
                    application
                    edit-application
                    (fn [response]
                      (rf/dispatch [::fetch-application (:application/id application) false])
                      (handle-validations! response description application {:on-success #(do (rf/dispatch [::set-autosaving false])
                                                                                              (flash-message/show-quiet-success! :actions [text :t.form/autosave-confirmed] {:content [[text-format :t.form/last-save (localize-time-with-seconds (time-core/now))]]}))
                                                                             :default-success? false
                                                                             :focus? false
                                                                             :warn-about-missing? false}))
                    {:error-handler (fn [err]
                                      (rf/dispatch [::set-autosaving false]))})
       {:db (-> db
                (assoc ::autosaving true)
                (assoc-in [::edit-application :validation :errors] nil)
                (assoc-in [::edit-application :validation :warnings] nil))})
     {})))

(rf/reg-event-fx
 ::submit-application
 (fn [{:keys [db]} [_ description]]
   (let [application (:data (::application db))
         edit-application (::edit-application db)]
     (save-draft! description
                  application
                  edit-application
                  (fn [response] ; because warnings are treated as errors in submit, skip validation handling here
                    (if-not (:success response)
                      (handle-validations! response description application)
                      (post! "/api/applications/submit"
                             {:params {:application-id (:application/id application)}
                              :handler (fn [response]
                                         (handle-validations!
                                          response
                                          description
                                          application
                                          {:on-success (fn [] (rf/dispatch [::fetch-application (:application/id application)]))}))
                              :error-handler (flash-message/default-error-handler :actions description)})))))
   {:db (-> db
            (assoc-in [::edit-application :validation :errors] nil)
            (assoc-in [::edit-application :validation :warnings] nil))}))

(rf/reg-event-fx
 ::copy-as-new-application
 (fn [{:keys [db]} _]
   (let [application-id (get-in db [::application :data :application/id])
         description [text :t.form/copy-as-new]]
     (post! "/api/applications/copy-as-new"
            {:params {:application-id application-id}
             :handler (flash-message/default-success-handler
                       :top ; the message will be shown on the new application's page
                       description
                       #(navigate! (str "/application/" (:application-id %))))
             :error-handler (flash-message/default-error-handler :actions description)}))
   {}))

(defn- save-attachment [{:keys [db]} [_ form-id field-id file]]
  (let [application-id (get-in db [::application :data :application/id])
        current-attachments (form/parse-attachment-ids (get-in db [::edit-application :field-values form-id field-id]))
        description [text :t.form/upload]
        config @(rf/subscribe [:rems.config/config])
        file-size (.. file (get "file") -size)
        file-name (.. file (get "file") -name)]
    (rf/dispatch [::set-attachment-status form-id field-id :pending])
    (if (some-> (:attachment-max-size config)
                (< file-size))
      (do
        (rf/dispatch [::set-attachment-status form-id field-id :error])
        (flash-message/show-default-error! :actions description
                                           [:div
                                            [:p [text :t.form/too-large-attachment]]
                                            [:p (str file-name " " (format-file-size file-size))]
                                            [:p [text-format :t.form/attachment-max-size (format-file-size (:attachment-max-size config))]]]))
      (post! "/api/applications/add-attachment"
             {:url-params {:application-id application-id}
              :body file
              ;; force saving a draft when you upload an attachment.
              ;; this ensures that the attachment is not left
              ;; dangling (with no references to it)
              :handler (fn [response]
                         ;; no need to check (:success response) - the API can't fail at the moment
                         ;; no race condition here: events are handled in a FIFO manner
                         (rf/dispatch [::set-field-value form-id field-id (form/unparse-attachment-ids
                                                                           (conj current-attachments (:id response)))])
                         (if (:enable-autosave config)
                           (do
                             (fields/always-on-change (:id response))
                             (rf/dispatch [::set-attachment-status form-id field-id :success]))
                           (rf/dispatch [::save-application description #(rf/dispatch [::set-attachment-status form-id field-id :success])])))
              :error-handler (fn [response]
                               (rf/dispatch [::set-attachment-status form-id field-id :error])
                               (cond (= 413 (:status response))
                                     (flash-message/show-default-error! :actions description
                                                                        [:div
                                                                         [:p [text :t.form/too-large-attachment]]
                                                                         [:p (str file-name " " (format-file-size file-size))]
                                                                         [:p [text-format :t.form/attachment-max-size (format-file-size (:attachment-max-size config))]]])

                                     (= 415 (:status response))
                                     (flash-message/show-default-error! :actions description
                                                                        [:div
                                                                         [:p [text :t.form/invalid-attachment]]
                                                                         [:p [text-format :t.form/upload-extensions attachment-util/allowed-extensions-string]]])

                                     :else ((flash-message/default-error-handler :actions description) response)))})))
  {})

(rf/reg-event-fx ::save-attachment save-attachment)

(rf/reg-event-fx
 ::remove-attachment
 (fn [{:keys [db]} [_ form-id field-id attachment-id]]
   (fields/always-on-change attachment-id)
   {:db (update-in db [::edit-application :field-values form-id field-id]
                   (comp form/unparse-attachment-ids
                         (partial remove #{attachment-id})
                         form/parse-attachment-ids))}))

(rf/reg-event-db
 ::set-field-value
 (fn [db [_ form-id field-id value]]
   (assoc-in db [::edit-application :field-values form-id field-id] value)))

(rf/reg-event-db
 ::set-attachment-status
 (fn [db [_ form-id field-id value]]
   (assoc-in db [::edit-application :attachment-status form-id field-id] value)))

(rf/reg-event-db
 ::toggle-diff
 (fn [db [_ form-id field-id]]
   (update-in db [::edit-application :show-diff form-id field-id] not)))

(rf/reg-event-db
 ::set-duo-codes
 (fn [db [_ duos]]
   (let [existing-codes (-> db ::edit-application :duo-codes)
         duos (for [duo duos]
                (update duo :restrictions #(build-index {:keys [:type] :value-fn :values} %)))]
     (-> db
         (assoc-in [::edit-application :duo-codes]
                   (->> duos
                        (map #(get existing-codes (:id %) %))
                        (build-index {:keys [:id]})))))))

(fetcher/reg-fetcher ::previous-applications "/api/applications")
(fetcher/reg-fetcher ::duo-codes "/api/resources/duo-codes")

(rf/reg-sub
 ::previous-applications-except-current
 (fn [& _]
   [(rf/subscribe [::application-id])
    (rf/subscribe [::previous-applications])
    (rf/subscribe [::previous-applications :error])
    (rf/subscribe [::previous-applications :initialized?])
    (rf/subscribe [::previous-applications :fetching?])
    (rf/subscribe [::previous-applications :searching?])])
 (fn [[application-id data error initialized? fetching? searching?]
      [_id key]]
   (case key
     :error error
     :initialized? initialized?
     :fetching? fetching?
     :searching? searching?
     nil (filterv #(not= application-id (:application/id %)) data))))

(rf/reg-event-db
 ::set-highlight-event-ids
 (fn [db [_ event-ids]]
   (assoc db ::highlight-event-ids event-ids)))

(rf/reg-sub
 ::highlight-event-ids
 (fn [db _]
   (set (::highlight-event-ids db))))

;;;; UI components

(defn- pdf-button [app-id]
  (when app-id
    [:a.btn.btn-secondary
     {:href (str "/api/applications/" app-id "/pdf")
      :target :_blank}
     [external-link] " PDF"]))

(defn- attachment-zip-button [application]
  (when-not (empty? (:application/attachments application))
    [:a.btn.btn-secondary
     {:href (str "/api/applications/" (:application/id application) "/attachments?all=false")
      :target :_blank}
     [file-download] " " (text :t.form/attachments-as-zip)]))

(defn- link-license [license]
  (let [title (localized (:license/title license))
        link (localized (:license/link license))]
    [:div
     [:a.license-title {:href link :target :_blank}
      title " " [external-link]]]))

(defn- text-license [license]
  (let [id (:license/id license)
        collapse-id (str "collapse" id)
        title (localized (:license/title license))
        text (localized (:license/text license))]
    [:div.license-panel
     [:span.license-title
      [:a.license-header.collapsed {:data-toggle "collapse"
                                    :href (str "#" collapse-id)
                                    :aria-expanded "false"
                                    :aria-controls collapse-id}
       title]]
     [:div.collapse {:id collapse-id
                     :ref focus-when-collapse-opened
                     :tab-index "-1"}
      [:div.license-block (str/trim (str text))]]]))

(defn- attachment-license [application license]
  (let [title (localized (:license/title license))
        language @(rf/subscribe [:language])
        link (str "/applications/" (:application/id application)
                  "/license-attachment/" (:license/id license)
                  "/" (name language))]
    [:a.license-title {:href link :target :_blank}
     title " " [file-download]]))

(defn license-field [application license show-accepted-licenses?]
  [:div.license.flex-row.d-flex
   [:div.mr-2 (when show-accepted-licenses?
                (if (:accepted license)
                  (success-symbol)
                  (make-empty-symbol (success-symbol))))]
   (case (:license/type license)
     :link [link-license license]
     :text [text-license license]
     :attachment [attachment-license application license]
     [fields/unsupported-field license])])

(defn- save-button []
  [button-wrapper {:id "save"
                   :text (text :t.form/save)
                   :on-click #(rf/dispatch [::save-application [text :t.form/save]])}])

(defn- submit-button []
  [button-wrapper {:id "submit"
                   :text (text :t.form/submit)
                   :class :btn-primary
                   :on-click #(rf/dispatch [::submit-application [text :t.form/submit]])}])

(defn- copy-as-new-button []
  [button-wrapper {:id "copy-as-new"
                   :text (text :t.form/copy-as-new)
                   :on-click #(rf/dispatch [::copy-as-new-application])}])

(rf/reg-sub
 ::get-field-value
 (fn [db [_ form-id field-id]]
   (get-in db [::edit-application :field-values form-id field-id])))

(rf/reg-sub
 ::get-field-diff
 (fn [db [_ form-id field-id]]
   (get-in db [::edit-application :show-diff form-id field-id])))

(rf/reg-sub
 ::field-validations
 (fn [db _]
   (let [validations (get-in db [::edit-application :validation])]
     (index-by [:form-id :field-id]
               (some seq [(:errors validations) (:warnings validations)])))))

(rf/reg-sub
 ::get-field-validation
 :<- [::field-validations]
 (fn [field-validations [_ form-id field-id]]
   (get-in field-validations [form-id field-id])))

(rf/reg-sub
 ::get-field-attachment-status
 (fn [db [_ form-id field-id]]
   (get-in db [::edit-application :attachment-status form-id field-id])))

(rf/reg-sub
 ::application-attachments-by-id
 :<- [::application]
 (fn [application _]
   (index-by [:attachment/id] (:application/attachments application))))

(rf/reg-sub
 ::get-attachment-by-id
 :<- [::application-attachments-by-id]
 (fn [attachments-by-id [_ attachment-id]]
   (attachments-by-id attachment-id)))

(defn- field-container
  "A container component for field that isolates the pure render component
  from re-frame state. Only depends on relevant fields, not the whole application
  to limit re-rendering and improve performance."
  [field]
  (let [form-id (:form/id field)
        field-id (:field/id field)
        field-value @(rf/subscribe [::get-field-value form-id field-id])
        attachments-by-ids (fn [ids]
                             (mapv (fn [id]
                                     @(rf/subscribe [::get-attachment-by-id id]))
                                   ids))
        depended-field-id (form/field-depends-on-field field)
        depended-value (when depended-field-id
                         {depended-field-id @(rf/subscribe [::get-field-value form-id depended-field-id])})]

    (when (and (form/field-visible? field depended-value)
               (not (:field/private field))) ; private fields will have empty value anyway
      [fields/field (merge field
                           {:field/value field-value
                            :diff @(rf/subscribe [::get-field-diff form-id field-id])
                            :validation @(rf/subscribe [::get-field-validation form-id field-id])
                            :on-change #(rf/dispatch [::set-field-value form-id field-id %])
                            :on-toggle-diff #(rf/dispatch [::toggle-diff form-id field-id])}
                           (when (= :attachment (:field/type field))
                             {:field/attachments (->> field-value
                                                      form/parse-attachment-ids
                                                      attachments-by-ids
                                                      ;; The field value can contain an id that's not in attachments when a new attachment has been
                                                      ;; uploaded, but the application hasn't yet been refetched.
                                                      (remove nil?))
                              :field/previous-attachments (when-let [prev (:field/previous-value field)]
                                                            (->> prev
                                                                 form/parse-attachment-ids
                                                                 attachments-by-ids))
                              :field/attachment-status @(rf/subscribe [::get-field-attachment-status form-id field-id])
                              :on-attach #(rf/dispatch [::save-attachment form-id field-id %1 %2])
                              :on-remove-attachment #(rf/dispatch [::remove-attachment form-id field-id %1])}))])))

(defn- application-fields [application]
  (let [language @(rf/subscribe [:language])]
    (into [:div]
          (for [form (:application/forms application)
                :let [form-id (:form/id form)]
                :when (->> (:form/fields form)
                           (remove :field/private)
                           seq)]
            [collapsible/component
             {:class "mb-3"
              :title (or (get-in form [:form/external-title language]) (text :t.form/application))
              :always (into [:div.fields]
                            (for [field (:form/fields form)]
                              [field-container (merge field
                                                      {:form/id form-id
                                                       :readonly @(rf/subscribe [::readonly?])
                                                       :app-id (:application/id application)})]))}]))))

(defn- application-licenses [application userid]
  (when-let [licenses (not-empty (:application/licenses application))]
    (let [application-id (:application/id application)
          roles (:application/roles application)
          show-accepted-licenses? (or (contains? roles :member)
                                      (contains? roles :applicant))
          accepted-licenses (get (:application/accepted-licenses application) userid)
          permissions (:application/permissions application)]
      [collapsible/component
       {:id "application-licenses"
        :title (text :t.form/licenses)
        :always
        [:div
         [flash-message/component :accept-licenses]
         [:p (text :t.form/must-accept-licenses)]
         (into [:div#licenses]
               (for [license (sort-by #(-> % :license/title localized) licenses)]
                 [license-field
                  application
                  (assoc license
                         :accepted (contains? accepted-licenses (:license/id license))
                         :readonly @(rf/subscribe [::readonly?]))
                  show-accepted-licenses?]))
         (when (contains? permissions :application.command/add-licenses)
           [:<>
            [:div.commands [add-licenses-action-button]]
            [add-licenses-form application-id (partial reload! application-id)]])
         (if (accepted-licenses? application userid)
           [:div#has-accepted-licenses (text :t.form/has-accepted-licenses)]
           (when (contains? permissions :application.command/accept-licenses)
             [:div.commands
              ;; TODO consider saving the form first so that no data is lost for the applicant
              [accept-licenses-action-button application-id (mapv :license/id licenses) #(reload! application-id)]]))]}])))

(defn- application-link [application prefix]
  (let [config @(rf/subscribe [:rems.config/config])]
    [:a {:href (str "/application/" (:application/id application))}
     (when prefix
       (str prefix " "))
     (application-list/format-application-id config application)]))

(defn- event-view [{:keys [attachments highlight set-highlights]} event]
  (let [event-text (localize-event event)
        decision (localize-decision event)
        comment (case (:event/type event)
                  :application.event/copied-from
                  [application-link (:application/copied-from event) (text :t.applications/application)]

                  :application.event/copied-to
                  [application-link (:application/copied-to event) (text :t.applications/application)]

                  (not-empty (:application/comment event)))
        time (localize-time (:event/time event))]
    [:div.row.event
     {:class (when highlight
               "border rounded border-primary")}
     [:label.col-sm-2.col-form-label time]
     [:div.col-sm-10
      [:div.event-description.d-flex.justify-content-between.col-form-label
       [:b.col.pl-0 event-text]
       (when (fn? set-highlights)
         [:div.d-inline-flex.align-items-start
          [:a {:href "#" :on-click set-highlights}
           (text :t.applications/highlight-related-events)]])]
      (when decision
        [:div.event-decision decision])
      (when comment
        [:div.form-control.event-comment comment])
      (when (seq attachments)
        [:div.event-attachments.pt-2
         [fields/render-attachments attachments]])]]))

(rf/reg-sub
 ::enrich-event-by-id
 :<- [::application]
 :<- [::highlight-event-ids]
 (fn [[application ids] [_ event-id]]
   {:highlight (contains? ids event-id)
    :attachments (for [att (:application/attachments application)
                       :when (= event-id (get-in att [:attachment/event :event/id]))]
                   att)}))

(defn- render-event [event]
  (let [opts (merge {:set-highlights (when-some [event-ids (seq (:highlight-event-ids event))]
                                       (fn []
                                         (rf/dispatch [::set-highlight-event-ids event-ids])))}
                    @(rf/subscribe [::enrich-event-by-id (:event/id event)]))]
    [event-view opts event]))

(defn- render-events [events]
  (for [e events]
    [render-event e]))

(defn- get-application-phases [state]
  (cond (contains? #{:application.state/rejected} state)
        [{:phase :apply :completed? true :text :t.phases/apply}
         {:phase :approve :completed? true :rejected? true :text :t.phases/approve}
         {:phase :result :completed? true :rejected? true :text :t.phases/rejected}]

        (contains? #{:application.state/revoked} state)
        [{:phase :apply :completed? true :text :t.phases/apply}
         {:phase :approve :completed? true :approved? true :text :t.phases/approve}
         {:phase :result :completed? true :revoked? true :text :t.phases/revoked}]

        (contains? #{:application.state/approved} state)
        [{:phase :apply :completed? true :text :t.phases/apply}
         {:phase :approve :completed? true :approved? true :text :t.phases/approve}
         {:phase :result :completed? true :approved? true :text :t.phases/approved}]

        (contains? #{:application.state/closed} state)
        [{:phase :apply :closed? true :text :t.phases/apply}
         {:phase :approve :closed? true :text :t.phases/approve}
         {:phase :result :closed? true :text :t.phases/approved}]

        (contains? #{:application.state/draft :application.state/returned} state)
        [{:phase :apply :active? true :text :t.phases/apply}
         {:phase :approve :text :t.phases/approve}
         {:phase :result :text :t.phases/approved}]

        (contains? #{:application.state/submitted} state)
        [{:phase :apply :completed? true :text :t.phases/apply}
         {:phase :approve :active? true :text :t.phases/approve}
         {:phase :result :text :t.phases/approved}]

        :else
        [{:phase :apply :active? true :text :t.phases/apply}
         {:phase :approve :text :t.phases/approve}
         {:phase :result :text :t.phases/approved}]))

(defn- application-copy-notice [application]
  (let [old-app (:application/copied-from application)
        new-apps (:application/copied-to application)]
    (when (or old-app new-apps)
      [:<>
       " ("
       (when old-app
         [:<> (text :t.applications/copied-from) " " [application-link old-app nil]])
       (when (and old-app new-apps)
         "; ")
       (when new-apps
         (into [:<> (text :t.applications/copied-to) " "]
               (interpose ", " (for [new-app new-apps]
                                 [application-link new-app nil]))))
       ")"])))

(defn- application-state-details [application config events]
  [:<>
   [:h3.mt-3 (text :t.applications/details)]
   [info-field
    (text :t.applications/application)
    [:<>
     [:span#application-id
      (application-list/format-application-id config application)]
     [application-copy-notice application]]
    {:inline? true}]
   (when-not (str/blank? (:application/description application))
     [info-field
      (text :t.applications/description)
      (:application/description application)
      {:inline? true}])
   [info-field
    (text :t.applications/state)
    [:span#application-state
     (localize-state (:application/state application))]
    {:inline? true}]
   [info-field
    (text :t.applications/latest-activity)
    (localize-time (:application/last-activity application))
    {:inline? true}]
   (when (seq events)
     (into [:<>
            [:h3.mt-3 (text :t.form/events)]]
           (render-events events)))])

(rf/reg-sub
 ::application-events
 :<- [::application]
 (fn [application _]
   (->> application
        :application/events
        (sort-by :event/time)
        (reverse))))

(defn- application-state [{:keys [application config userid]}]
  (let [state (:application/state application)
        events @(rf/subscribe [::application-events])
        [events-show-always events-collapse] (split-at 3 events)]
    [collapsible/component
     (-> {:id "header"
          :title (text :t.applications/state)
          :always [:div
                   [:div.mb-3
                    [phases state (get-application-phases state)]]
                   (when (is-handler? application userid)
                     (->> events-show-always
                          (application-state-details application config)))]
          :collapse-hidden (when (and (not (is-handler? application userid))
                                      (seq events))
                             [:div.mt-3.mb-3
                              [render-event (first events)]])
          :collapse (if (is-handler? application userid)
                      (when (seq events-collapse)
                        (into [:div]
                              (render-events events-collapse)))
                      (->> (concat events-show-always events-collapse)
                           (application-state-details application config)))
          :on-close #(rf/dispatch [::set-highlight-event-ids []])} ; remove highlights on toggle close
         (assoc-some :open? (seq @(rf/subscribe [::highlight-event-ids]))))])) ; show all events on highlight

(defn member-info
  "Renders a applicant, member or invited member of an application

  `:element-id`         - id of the element to generate unique ids
  `:attributes`         - user attributes to display
  `:application`        - application
  `:group?`             - specifies if a group border is rendered
  `:simple?`            - specifies if we want to simplify by leaving out expansion and heading, defaults to false"
  [{:keys [element-id attributes application group? simple?] :or {simple? false}}]
  (let [application-id (:application/id application)
        user-id (:userid attributes)
        invited-user? (nil? user-id)
        applicant? (= (:userid (:application/applicant application)) user-id)
        title (cond applicant? (text :t.applicant-info/applicant)
                    invited-user? (text :t.applicant-info/invited-member)
                    :else (text :t.applicant-info/member))
        accepted? (accepted-licenses? application user-id)
        permissions (:application/permissions application)
        can-remove? (and (not applicant?)
                         (contains? permissions :application.command/remove-member))
        can-uninvite? (and invited-user?
                           (contains? permissions :application.command/uninvite-member))
        can-change? (and (not applicant?)
                         (not invited-user?)
                         (contains? permissions :application.command/change-applicant))]
    [collapsible/minimal
     {:id (str element-id "-info")
      :class (when group? "group")
      :always [:div
               (when-not simple? [:h3 title])
               [user/username attributes]
               (when-not (or invited-user?
                             simple?
                             (= :application.state/draft (:application/state application)))
                 [info-field (text :t.form/accepted-licenses) [readonly-checkbox {:value accepted?}] {:inline? true}])]
      :collapse [user/attributes attributes invited-user?]
      :footer (let [element-id (str element-id "-operations")]
                [:div {:id element-id}
                 [:div.commands
                  (when can-change?
                    [change-applicant-action-button element-id])
                  (when (or can-remove? can-uninvite?)
                    [remove-member-action-button element-id])]
                 [change-applicant-form element-id attributes application-id (partial reload! application-id)]
                 [remove-member-form element-id attributes application-id (partial reload! application-id)]])}]))

(defn- applicants-details [application & [opts]]
  (let [applicant (:application/applicant application)
        members (:application/members application)
        invited-members (:application/invited-members application)]
    (into [:div
           [flash-message/component :change-members]
           [member-info {:element-id "applicant"
                         :attributes applicant
                         :application application
                         :simple? (:simple? opts false)
                         :group? (:group? opts (not (:simple? opts false)))}]]
          (concat
           (for [[index member] (map-indexed vector (sort-by :name members))]
             [member-info {:element-id (str "member" index)
                           :attributes member
                           :application application
                           :group? true}])
           (for [[index invited-member] (map-indexed vector (sort-by :name invited-members))]
             [member-info {:element-id (str "invite" index)
                           :attributes invited-member
                           :application application
                           :group? true}])))))

(defn- applicants-short [application]
  (let [applicant (:application/applicant application)
        members (:application/members application)
        invited-members (:application/invited-members application)]
    (into [:div]
          (->> (union #{applicant} members invited-members)
               (keep get-member-name)
               sort
               (str/join ", ")))))

(defn applicants-info
  "Renders the applicants, i.e. applicant and members."
  [application userid]
  (let [application-id (:application/id application)
        permissions (:application/permissions application)
        can-add? (contains? permissions :application.command/add-member)
        can-invite? (contains? permissions :application.command/invite-member)
        ;; XXX: we could have the application model include this information more reliably
        only-one-applicant? (and (not can-add?)
                                 (not can-invite?)
                                 (= 1
                                    (count (concat [(:application/applicant application)]
                                                   (:application/members application)
                                                   (:application/invited-members application)))))
        component {:id "applicants-info"
                   :title (if only-one-applicant?
                            (text :t.applicant-info/applicant)
                            (text :t.applicant-info/applicants))
                   :footer [:div
                            [:div.commands
                             (when can-invite? [invite-member-action-button])
                             (when can-add? [add-member-action-button])]
                            [:div#member-action-forms
                             [invite-member-form application-id (partial reload! application-id)]
                             [add-member-form application-id (partial reload! application-id)]]]}]
    [collapsible/component
     (cond (is-handler? application userid)
           (assoc component
                  :always (applicants-details application))

           only-one-applicant?
           (assoc component
                  :always (applicants-details application {:simple? true}))

           :else
           (assoc component
                  :collapse-hidden (applicants-short application)
                  :collapse (applicants-details application)))]))

(defn- request-review-dropdown []
  [:div.btn-group
   [:button#request-review-dropdown.btn.btn-secondary.dropdown-toggle
    {:data-toggle :dropdown}
    (text :t.actions/request-review-dropdown)]
   [:div.dropdown-menu
    [request-review-action-link]
    [invite-reviewer-action-link]]])

(defn- request-decision-dropdown []
  [:div.btn-group
   [:button#request-decision-dropdown.btn.btn-secondary.dropdown-toggle
    {:data-toggle :dropdown}
    (text :t.actions/request-decision-dropdown)]
   [:div.dropdown-menu
    [request-decision-action-link]
    [invite-decider-action-link]]])

(defn- action-buttons [application config]
  (let [commands-and-actions (concat
                              (when-not (:enable-autosave config)
                                ;; no explicit command for :application.command/save-draft when autosave
                                [:application.command/save-draft [save-button]])

                              [:application.command/submit [submit-button]
                               :application.command/return [return-action-button]
                               ;; this assumes that request-review and invite-reviewer are both possible or neither is
                               :application.command/request-review [request-review-dropdown]
                               :application.command/invite-reviewer [request-review-dropdown]
                               :application.command/review [review-action-button]
                               ;; ditto for decision
                               :application.command/request-decision [request-decision-dropdown]
                               :application.command/invite-decider [request-decision-dropdown]
                               :application.command/decide [decide-action-button]
                               :application.command/remark [remark-action-button]
                               :application.command/approve [approve-reject-action-button]
                               :application.command/reject [approve-reject-action-button]
                               :application.command/revoke [revoke-action-button]
                               :application.command/assign-external-id (when (:enable-assign-external-id-ui @(rf/subscribe [:rems.config/config]))
                                                                         [assign-external-id-button (get application :application/assigned-external-id "")])
                               :application.command/close [close-action-button]
                               :application.command/delete [delete-action-button]
                               :application.command/copy-as-new [copy-as-new-button]
                               :application.command/redact-attachments (when-some [attachments (seq (filter :attachment/can-redact (:application/attachments application)))]
                                                                         [redact-attachments-action-button attachments])])]

    (-> (for [[command action] (partition 2 commands-and-actions)
              :when (contains? (:application/permissions application) command)]
          action)

        (concat [(when (:show-pdf-action config) [pdf-button (:application/id application)])
                 (when (:show-attachment-zip-action config) [attachment-zip-button application])])

        (->> (remove nil?))
        distinct)))

(defn- actions-form [application config]
  (let [app-id (:application/id application)
        ;; The :see-everything permission is used to determine whether the user
        ;; is allowed to see all comments. It would not make sense for the user
        ;; to be able to write a comment which he then cannot see.
        show-comment-field? (contains? (:application/permissions application) :see-everything)
        actions (action-buttons application config)
        reload (partial reload! app-id)
        go-to-catalogue #(do (flash-message/show-default-success! :top [text :t.actions/delete])
                             (navigate! "/catalogue"))]
    (when (seq actions)
      [collapsible/component
       {:id "actions-collapse"
        :title (text :t.form/actions)
        :always [:div

                 (into [:div#action-commands] actions)

                 [:div#actions-forms.mt-3
                  [request-review-form app-id reload]
                  [request-decision-form app-id reload]
                  [invite-decider-form app-id reload]
                  [invite-reviewer-form app-id reload]
                  [review-form app-id reload]
                  [remark-form app-id reload]
                  [redact-attachments-form app-id reload]
                  [close-form app-id show-comment-field? reload]
                  [revoke-form app-id reload]
                  [decide-form app-id reload]
                  [return-form app-id reload]
                  [approve-reject-form app-id reload]
                  [assign-external-id-form app-id reload]
                  [delete-form app-id go-to-catalogue]]]}])))

(defn- render-resource [resource language]
  (let [config @(rf/subscribe [:rems.config/config])
        duos (get-in resource [:resource/duo :duo/codes])
        resource-header [:p
                         (localized (:catalogue-item/title resource))
                         (when-let [url (catalogue-item-more-info-url resource language config)]
                           [:<>
                            " – "
                            [:a {:href url :target :_blank}
                             (text :t.catalogue/more-info) " " [external-link]]])]]
    [:div.application-resource
     (if-not (and (:enable-duo config) (seq duos))
       resource-header
       [expander
        {:id (str "resource-" (:resource/id resource) "-duos-collapsible")
         :title resource-header
         :content [collapsible/component
                   {:id (str "resource-" (:resource/id resource) "-duos")
                    :class "mt-3"
                    :title (text :t.duo/title)
                    :always [:div
                             (for [duo duos]
                               ^{:key (:id duo)}
                               [duo-info-field {:id (str "resource-" (:resource/id resource) "-duo-" (:id duo) "-collapsible")
                                                :compact? true
                                                :duo duo
                                                :duo/more-infos (when (:more-info duo)
                                                                  (list (select-keys duo [:more-info])))}])]}]}])]))

(defn- applied-resources [application userid language]
  (let [application-id (:application/id application)
        permissions (:application/permissions application)
        applicant? (= (:userid (:application/applicant application)) userid)
        can-change-resources? (contains? permissions :application.command/change-resources)
        can-comment? (not applicant?)]
    [collapsible/component
     {:id "resources"
      :title (text :t.form/resources)
      :always [:div.form-items.form-group
               [flash-message/component :change-resources]
               (into [:div.application-resources]
                     (for [resource (:application/resources application)]
                       ^{:key (:catalogue-item/id resource)}
                       [render-resource resource language]))]
      :footer [:div
               [:div.commands
                (when can-change-resources? [change-resources-action-button (:application/resources application)])]
               [:div#resource-action-forms
                [change-resources-form application can-comment? (partial reload! application-id true)]]]}]))

(defn- previous-applications [applicant]
  ;; print mode forces the collapsible open, so fetch the content proactively
  ;; TODO figure out a better solution
  (rf/dispatch [::previous-applications {:query (str "(applicant:\"" applicant "\" OR member:\"" applicant "\") AND -state:draft")}])
  (when (seq @(rf/subscribe [::previous-applications-except-current]))
    [collapsible/component
     {:id "previous-applications"
      :title (text :t.form/previous-applications)
      :collapse [:div.lg-fs70pct
                 [application-list/component {:applications ::previous-applications-except-current
                                              :hidden-columns #{:created :handlers :todo :last-activity :applicant}
                                              :default-sort-column :submitted
                                              :default-sort-order :desc}]]}]))

(rf/reg-sub ::duo-form
            :<- [::edit-application]
            (fn [edit-application] (:duo-codes edit-application)))
(rf/reg-event-db ::set-duo-form-code
                 (fn [db [_ keys value]] (assoc-in db (concat [::edit-application :duo-codes] keys) value)))

(defn- find-duo-more-info [duo]
  (for [resource (:application/resources @(rf/subscribe [::application]))
        res-duo (filter :more-info (-> resource :resource/duo :duo/codes))
        :when (= (:id res-duo) (:id duo))]
    (merge (select-keys res-duo [:more-info])
           (select-keys resource [:resource/id :catalogue-item/title]))))

(def ^:private duo-context
  {:get-form ::duo-form
   :update-form ::set-duo-form-code})

(defn- edit-application-duo-codes []
  (let [application-duo-matches (-> @(rf/subscribe [::application]) :application/duo :duo/matches)
        selected-duos (vals @(rf/subscribe [::duo-form]))]
    [collapsible/component
     {:id "duo-codes"
      :title (text :t.duo/title)
      :always [:div.form-items.form-group
               (when-let [missing-duos (seq (unmatched-duos application-duo-matches))]
                 [:div.alert.alert-warning
                  [:p (text :t.applications.duos.validation/missing-duo-codes)]
                  [:ul (for [duo missing-duos
                             :let [label (localized (:duo/label duo))]]
                         ^{:key (:duo/id duo)}
                         [:li (text-format :t.label/dash
                                           (:duo/shorthand duo) label)])]])
               [:div.mb-3
                [:label.administration-field-label {:for "duos-dropdown"} (text :t.duo/title)]
                [dropdown/dropdown
                 {:id "duos-dropdown"
                  :items @(rf/subscribe [::duo-codes])
                  :item-key :id
                  :item-label (fn [{:keys [id shorthand label]}]
                                (if-not (str/blank? shorthand)
                                  (str shorthand " – " (localized label))
                                  (str id " – " (localized label))))
                  :item-selected? (fn [duo] (some #(= (:id duo) (:id %)) selected-duos))
                  :multi? true
                  :on-change #(do (fields/always-on-change %) (rf/dispatch [::set-duo-codes %]))}]]
               (doall
                (for [edit-duo (sort-by :id selected-duos)
                      :let [duo-matches (->> application-duo-matches
                                             (filter (fn [match] (= (:id edit-duo) (:duo/id match)))))]]
                  ^{:key (:id edit-duo)}
                  [:div.form-field
                   [duo-field edit-duo {:context duo-context
                                        :on-change fields/always-on-change
                                        :duo/statuses (map (comp :validity :duo/validation) duo-matches)
                                        :duo/errors (mapcat (comp :errors :duo/validation) duo-matches)
                                        :duo/more-infos (find-duo-more-info edit-duo)}]]))]}]))

(defn- group-duos-by-matches [matches duos]
  (->> matches
       (remove #(= :duo/not-found (-> % :duo/validation :validity)))
       (build-index {:keys [:duo/id] :collect-fn identity})
       (map (fn [[duo-id matches]]
              {:duo (find-first #(= duo-id (:id %)) duos)
               :matches matches
               :validity (duo-validation-summary (map #(-> % :duo/validation :validity) matches))}))))

(defn- duo-status-sort-order [status]
  (case status
    :duo/not-compatible 0
    :duo/needs-manual-validation 1
    :duo/compatible 2
    3))

(defn- application-duo-codes []
  (let [duo-matches (-> @(rf/subscribe [::application]) :application/duo :duo/matches)
        duo-codes (-> @(rf/subscribe [::application]) :application/duo :duo/codes)]
    [collapsible/component
     {:id "duo-codes"
      :title (text :t.duo/title)
      :always (if (empty? duo-matches)
                [:p (text :t.duo/no-duo-codes)]
                [:div.form-items.form-group
                 (when-let [missing-duos (seq (unmatched-duos duo-matches))]
                   [:div.alert.alert-danger
                    [:p (text :t.applications.duos.validation/missing-duo-codes)]
                    [:ul
                     (for [duo missing-duos]
                       ^{:key (str "missing-duo-" (:duo/id duo))}
                       [:li (str (:duo/shorthand duo) " - " (localized (:duo/label duo)))])]])
                 (doall
                  (for [{:keys [duo matches]} (->> (group-duos-by-matches duo-matches duo-codes)
                                                   (sort-by (comp duo-status-sort-order :validity)))]
                    ^{:key (:id duo)}
                    [duo-info-field {:id (str "duo-info-field-" (:id duo))
                                     :duo duo
                                     :duo/matches matches
                                     :duo/more-infos (find-duo-more-info duo)}]))])}]))

(defn- get-resource-duos [application]
  (let [duos (->> (:application/resources application)
                  (mapcat #(get-in % [:resource/duo :duo/codes])))]
    duos))

(defn- render-application [{:keys [application config userid language]}]
  [:<>
   [disabled-items-warning application]
   [blacklist-warning application]
   (text :t.applications/intro)
   [:div.row
    [:div.col-lg-8.application-content
     [application-state {:application application
                         :config config
                         :userid userid}]
     [:div.mt-3 [applicants-info application userid]]
     (when (:show-resources-section config)
       [:div.mt-3 [applied-resources application userid language]])
     (when (and (:enable-duo config)
                (seq (get-resource-duos application)))
       (if @(rf/subscribe [::readonly?])
         [:div.mt-3 [application-duo-codes]]
         [:div.mt-3 [edit-application-duo-codes]]))
     (when (contains? (:application/permissions application) :see-everything)
       [:div.mt-3 [previous-applications (get-in application [:application/applicant :userid])]])
     [:div.my-3 [application-licenses application userid]]
     [:div.mt-3 [application-fields application]]]
    [:div.col-lg-4.spaced-vertically-3
     [:div#actions
      [flash-message/component :actions]
      (when (and (not @(rf/subscribe [::flash-message/message :actions])) ; avoid two messages at the same time and causing re-layout
                 @(rf/subscribe [::autosaving]))
        [:div.alert.alert-info
         [text :t.form/autosave-in-progress]
         [:span.ml-2 [spinner/small]]])
      [actions-form application config]]]]])

;;;; Entrypoint

(defn application-page []
  (let [config @(rf/subscribe [:rems.config/config])
        application-id @(rf/subscribe [::application-id])
        application @(rf/subscribe [::application])
        loading? @(rf/subscribe [::application :loading?])
        reloading? @(rf/subscribe [::application :reloading?])
        userid (:userid @(rf/subscribe [:user]))
        language @(rf/subscribe [:language])]
    [:div.container-fluid
     [document-title (str (text :t.applications/application)
                          (when application
                            (str " " (application-list/format-application-id config application)))
                          (when-not (str/blank? (:application/description application))
                            (str ": " (:application/description application))))]
     ^{:key application-id} ; re-render to clear flash messages when navigating to another application
     [:<>
      [flash-message/component :top]
      [flash-message/component :top-validation]]
     (when loading?
       [spinner/big])
     (when application
       [render-application {:application application
                            :config config
                            :userid userid
                            :language language}])
     ;; Located after the application to avoid re-rendering the application
     ;; when this element is added or removed from virtual DOM.
     (when reloading?
       [:div.reload-indicator
        [spinner/small]])]))

;;;; Guide

(defn guide []
  [:div
   (component-info member-info)
   (example "member-info: applicant with notification email, accepted licenses, researcher status"
            [member-info {:element-id "info1"
                          :attributes {:userid "developer@uu.id"
                                       :email "developer@uu.id"
                                       :name "Deve Loper"
                                       :notification-email "notification@example.com"
                                       :organizations [{:organization/id "Testers"} {:organization/id "Users"}]
                                       :address "Testikatu 1, 00100 Helsinki"
                                       :researcher-status-by "so"}
                          :application {:application/id 42
                                        :application/applicant {:userid "developer@uu.id"}
                                        :application/licenses [{:license/id 1}]
                                        :application/accepted-licenses {"developer@uu.id" #{1}}}}])
   (example "member-info with name missing"
            [member-info {:element-id "info2"
                          :attributes {:userid "developer"
                                       :email "developer@uu.id"
                                       :address "Testikatu 1, 00100 Helsinki"}}])
   (example "member-info with buttons, licenses not accepted"
            [member-info {:element-id "info3"
                          :attributes {:userid "alice"}
                          :application {:application/id 42
                                        :application/applicant {:userid "developer"}
                                        :application/licenses [{:license/id 1}]
                                        :application/permissions #{:application.command/remove-member
                                                                   :application.command/change-applicant}}
                          :group? true}])
   (example "member-info: invited member"
            [member-info {:element-id "info4"
                          :attributes {:name "John Smith"
                                       :email "john.smith@invited.com"}
                          :application {:application/id 42
                                        :application/applicant {:userid "developer"}}
                          :group? true}])
   (example "member-info: invited member, with remove button"
            [member-info {:element-id "info5"
                          :attributes {:name "John Smith"
                                       :email "john.smith@invited.com"}
                          :application {:application/id 42
                                        :application/applicant {:userid "developer"}
                                        :application/permissions #{:application.command/uninvite-member}}
                          :group? true}])

   (example "member-info: not grouped"
            [member-info {:element-id "info6"
                          :group? false
                          :attributes {:userid "developer@uu.id"
                                       :email "developer@uu.id"
                                       :name "Deve Loper"}}])

   (example "member-info: simple"
            [member-info {:element-id "info7"
                          :simple? true
                          :attributes {:userid "developer@uu.id"
                                       :email "developer@uu.id"
                                       :name "Deve Loper"}}])

   (component-info applicants-info)
   (example "applicants-info: multiple applicants"
            [applicants-info {:application/id 42
                              :application/applicant {:userid "developer"
                                                      :email "developer@uu.id"
                                                      :name "Deve Loper"}
                              :application/members #{{:userid "alice"}
                                                     {:userid "bob"}}
                              :application/invited-members #{{:name "John Smith" :email "john.smith@invited.com"}}
                              :application/licenses [{:license/id 1}]
                              :application/accepted-licenses {"developer" #{1}}
                              :application/permissions #{:application.command/add-member
                                                         :application.command/invite-member}}])

   (example "applicants-info: only one applicant without permissions should show a simple singular block"
            [applicants-info {:application/id 42
                              :application/applicant {:userid "developer"
                                                      :email "developer@uu.id"
                                                      :name "Deve Loper"}
                              :application/members #{}
                              :application/invited-members #{}
                              :application/licenses [{:license/id 1}]
                              :application/accepted-licenses {"developer" #{1}}
                              :application/permissions #{}}])

   (component-info disabled-items-warning)
   (example "should be hidden for an applicant"
            [disabled-items-warning {:application/state :application.state/submitted
                                     :application/resources [{:catalogue-item/enabled false :catalogue-item/archived false
                                                              :catalogue-item/title {:en "Disabled catalogue item"}}]}])
   (example "no disabled items"
            [disabled-items-warning {:application/permissions #{:see-everything}}])
   (example "two disabled items"
            [disabled-items-warning
             {:application/permissions #{:see-everything}
              :application/state :application.state/submitted
              :application/resources [{:catalogue-item/enabled true :catalogue-item/archived true
                                       :catalogue-item/title {:en "Catalogue item 1"}}
                                      {:catalogue-item/enabled false :catalogue-item/archived false
                                       :catalogue-item/title {:en "Catalogue item 2"}}
                                      {:catalogue-item/enabled true :catalogue-item/archived false
                                       :catalogue-item/title {:en "Catalogue item 3"}}]}])
   (component-info blacklist-warning)
   (example "no blacklist"
            [blacklist-warning {}])
   (example "three entries"
            [blacklist-warning {:application/resources [{:resource/ext-id "urn:11"
                                                         :catalogue-item/title {:fi "11"
                                                                                :sv "11"
                                                                                :en "11"}}
                                                        {:resource/ext-id "urn:12"
                                                         :catalogue-item/title {:fi "12"
                                                                                :sv "12"
                                                                                :en "12"}}]
                                :application/blacklist [{:blacklist/user {:userid "user1" :name "First User" :email "first@example.com"}
                                                         :blacklist/resource {:resource/ext-id "urn:11"}}
                                                        {:blacklist/user {:userid "user1" :name "First User" :email "first@example.com"}
                                                         :blacklist/resource {:resource/ext-id "urn:12"}}
                                                        {:blacklist/user {:userid "user2" :name "Second User" :email "second@example.com"}
                                                         :blacklist/resource {:resource/ext-id "urn:11"}}]}])

   (component-info license-field)
   (example "link license"
            [license-field
             {:application/id 123}
             {:license/id 1
              :license/type :link
              :license/title {:en "Link to license"}
              :license/link {:en "https://creativecommons.org/licenses/by/4.0/deed.en"}}
             false])
   (example "link license, not accepted"
            [license-field
             {:application/id 123}
             {:license/id 1
              :license/type :link
              :license/title {:en "Link to license"}
              :license/link {:en "https://creativecommons.org/licenses/by/4.0/deed.en"}
              :accepted false}
             true])
   (example "link license, accepted"
            [license-field
             {:application/id 123}
             {:license/id 1
              :license/type :link
              :license/title {:en "Link to license"}
              :license/link {:en "https://creativecommons.org/licenses/by/4.0/deed.en"}
              :accepted true}
             true])
   (example "text license"
            [license-field
             {:application/id 123}
             {:license/id 1
              :license/type :text
              :license/title {:en "A Text License"}
              :license/text {:en lipsum-paragraphs}}
             false])
   (example "attachment license"
            [license-field
             {:application/id 123}
             {:license/id 1
              :license/type :attachment
              :license/title {:en "A Text License"}
              :license/text {:en lipsum-paragraphs}}
             false])

   (component-info actions-form)
   (example "no actions available"
            [actions-form {:application/id 123
                           :application/permissions #{}}
             {}])

   (example "some actions available"
            [actions-form {:application/id 123
                           :application/permissions #{:application.command/save-draft}}
             {:show-pdf-action true}])

   (example "all actions available"
            [actions-form {:application/id 123
                           :application/permissions #{:application.command/save-draft
                                                      :application.command/submit
                                                      :application.command/return
                                                      :application.command/request-review
                                                      :application.command/review
                                                      :application.command/request-decision
                                                      :application.command/decide
                                                      :application.command/remark
                                                      :application.command/approve
                                                      :application.command/reject
                                                      :application.command/revoke
                                                      :application.command/assign-external-id
                                                      :application.command/close
                                                      :application.command/copy-as-new}
                           :application/attachments [{:attachment/filename "foo.txt"} {:attachment/filename "bar.txt"}]}])

   (component-info render-application)
   (example "application, partially filled, as applicant, one unsupported field"
            [render-application
             {:application {:application/id 17
                            :application/applicant {:userid "applicant"}
                            :application/roles #{:applicant}
                            :application/permissions #{:application.command/accept-licenses}
                            :application/state :application.state/draft
                            :application/resources [{:catalogue-item/title {:en "An applied item"}}]
                            :application/forms [{:form/id 1
                                                 :form/fields [{:field/id "fld1"
                                                                :field/type :text
                                                                :field/title {:en "Field 1"}
                                                                :field/placeholder {:en "placeholder 1"}}
                                                               {:field/id "fld2"
                                                                :field/type :label
                                                                :title "Please input your wishes below."}
                                                               {:field/id "fld3"
                                                                :field/type :texta
                                                                :field/optional true
                                                                :field/title {:en "Field 2"}
                                                                :field/placeholder {:en "placeholder 2"}}
                                                               {:field/id "fld4"
                                                                :field/type :unsupported
                                                                :field/title {:en "Field 3"}
                                                                :field/placeholder {:en "placeholder 3"}}
                                                               {:field/id "fld5"
                                                                :field/type :date
                                                                :field/title {:en "Field 4"}}]}]
                            :application/licenses [{:license/id 4
                                                    :license/type :text
                                                    :license/title {:en "A Text License"}
                                                    :license/text {:en lipsum}}
                                                   {:license/id 5
                                                    :license/type :link
                                                    :license/title {:en "Link to license"}
                                                    :license/link {:en "https://creativecommons.org/licenses/by/4.0/deed.en"}}]}
              :edit-application {:field-values {1 {"fld1" "abc"}}
                                 :show-diff {}
                                 :validation {:errors nil :warnings nil}
                                 :accepted-licenses {"applicant" #{5}}}
              :userid "applicant"}])
   (example "application, applied"
            [render-application
             {:application {:application/id 17
                            :application/state :application.state/submitted
                            :application/resources [{:catalogue-item/title {:en "An applied item"}}]
                            :application/forms [{:form/id 1
                                                 :form/fields [{:field/id "fld1"
                                                                :field/type :text
                                                                :field/title {:en "Field 1"}
                                                                :field/placeholder {:en "placeholder 1"}}]}]
                            :application/licenses [{:license/id 4
                                                    :license/type :text
                                                    :license/title {:en "A Text License"}
                                                    :license/text {:en lipsum}}]}}])
   (example "application, approved"
            [render-application
             {:application {:application/id 17
                            :application/state :application.state/approved
                            :application/applicant {:userid "userid"
                                                    :email "email@example.com"
                                                    :additional "additional field"}
                            :application/resources [{:catalogue-item/title {:en "An applied item"}}]
                            :application/forms [{:form/id 1
                                                 :form/fields [{:field/id "fld1"
                                                                :field/type :text
                                                                :field/title {:en "Field 1"}
                                                                :field/placeholder {:en "placeholder 1"}}]}]
                            :application/licenses [{:license/id 4
                                                    :license/type :text
                                                    :license/title {:en "A Text License"}
                                                    :license/text {:en lipsum}}]}}])

   (component-info event-view)
   (example "simple event"
            [event-view nil {:event/time #inst "2020-01-01T08:35"
                             :event/type :application.event/submitted
                             :event/actor-attributes {:userid "alice" :name "Alice Applicant"}}])
   (example "event with comment & decision"
            [event-view nil {:event/time #inst "2020-01-01T08:35"
                             :event/type :application.event/decided
                             :event/actor-attributes {:name "Hannah Handler"}
                             :application/decision :rejected
                             :application/comment "This application is bad."}])
   (example "event with comment & decision, highlighted"
            (let [opts {:highlight true}]
              [event-view opts {:event/time #inst "2020-01-01T08:35"
                                :event/type :application.event/decided
                                :event/actor-attributes {:name "Hannah Handler"}
                                :application/decision :rejected
                                :application/comment "This application is bad."}]))
   (example "event that triggers highlight on other events"
            (let [opts {:set-highlights #(println "set highlights")}]
              [event-view opts {:event/time #inst "2020-01-01T08:37"
                                :event/type :application.event/review-requested
                                :event/actor-attributes {:name "Hannah Handler"}
                                :application/reviewers [{:name "Carl Reviewer"}]
                                :application/comment "Please take a look"}]))
   (example "event with comment & attachment"
            (let [opts {:attachments [{:attachment/filename "verylongfilename_loremipsum_dolorsitamet.pdf"}]}]
              [event-view opts {:event/time #inst "2020-01-01T08:35"
                                :event/type :application.event/remarked
                                :event/actor-attributes {:name "Hannah Handler"}
                                :application/comment (str lipsum "\n\nA final line.")}]))
   (example "event with redacted attachments"
            (let [opts {:attachments [{:attachment/filename "regular_file.pdf"}
                                      {:attachment/filename "regular_file.pdf"
                                       :attachment/redacted true}
                                      {:attachment/filename :filename/redacted
                                       :attachment/redacted true}]}]
              [event-view opts {:event/time #inst "2020-01-01T08:35"
                                :event/type :application.event/remarked
                                :event/actor-attributes {:name "Hannah Handler"}
                                :application/comment (str lipsum "\n\nA final line.")}]))
   (example "event with many attachments"
            (let [opts {:attachments (repeat 30 {:attachment/filename "image.jpeg"})}]
              [event-view opts {:event/time #inst "2020-01-01T08:35"
                                :event/type :application.event/approved
                                :event/actor-attributes {:name "Hannah Handler"}}]))

   (component-info application-copy-notice)
   (example "no copies"
            [application-copy-notice {}])
   (example "copied from"
            [application-copy-notice {:application/copied-from {:application/id 1
                                                                :application/external-id "2018/10"}}])
   (example "copied to one"
            [application-copy-notice {:application/copied-to [{:application/id 2
                                                               :application/external-id "2019/20"}]}])
   (example "copied to many"
            [application-copy-notice {:application/copied-to [{:application/id 2
                                                               :application/external-id "2019/20"}
                                                              {:application/id 3
                                                               :application/external-id "2020/30"}]}])
   (example "copied to and from"
            [application-copy-notice {:application/copied-from {:application/id 1
                                                                :application/external-id "2018/10"}
                                      :application/copied-to [{:application/id 2
                                                               :application/external-id "2019/20"}
                                                              {:application/id 3
                                                               :application/external-id "2020/30"}]}])])
