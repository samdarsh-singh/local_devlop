(ns ^:integration rems.api.test-workflows
  (:require [clojure.test :refer :all]
            [rems.service.licenses :as licenses]
            [rems.service.workflow :as workflows]
            [rems.api.testing :refer :all]
            [rems.common.util :refer [replace-key]]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.db.test-data-users :refer [+fake-user-data+]]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.db.testing :refer [owners-fixture +test-api-key+]]
            [rems.db.workflow :as workflow]
            [rems.handler :refer [handler]]
            [rems.testing-util :refer [with-user]]
            [ring.mock.request :refer :all]))

(use-fixtures :each
  api-fixture
  owners-fixture)

;; this is a subset of what we expect to get from the api
(def ^:private expected
  {:organization {:organization/id "organization1"
                  :organization/name {:fi "Organization 1" :en "Organization 1" :sv "Organization 1"}
                  :organization/short-name {:fi "ORG 1" :en "ORG 1" :sv "ORG 1"}}
   :title "workflow title"
   :workflow {:type "workflow/default"
              :forms []
              :handlers [{:userid "handler" :email "handler@example.com" :name "Hannah Handler"}
                         {:userid "carl" :email "carl@example.com" :name "Carl Reviewer"}]
              :licenses []}
   :enabled true
   :archived false})

(defn- create-handlers! []
  (test-helpers/create-user! (+fake-user-data+ "handler"))
  (test-helpers/create-user! (+fake-user-data+ "carl"))
  (test-helpers/create-user! (+fake-user-data+ "alice")))

(defn- fetch [api-key user-id wfid]
  (-> (request :get (str "/api/workflows/" wfid))
      (authenticate api-key user-id)
      handler
      read-ok-body
      (select-keys (keys expected))))

(deftest workflows-api-test
  (create-handlers!)
  (let [create-workflow (fn [user-id organization type forms]
                          (api-call :post "/api/workflows/create"
                                    {:organization {:organization/id organization}
                                     :title "workflow title"
                                     :type type
                                     :forms forms
                                     :handlers ["handler" "carl"]}
                                    +test-api-key+ user-id))]
    (doseq [user-id ["owner" "organization-owner1"]]
      (testing user-id
        (let [id (test-helpers/create-workflow! {})]
          (testing "get by id"
            (let [data (api-call :get (str "/api/workflows/" id) nil
                                 +test-api-key+ user-id)]
              (is (= id (:id data)))))

          (testing "id not found"
            (is (response-is-not-found? (api-response :get (str "/api/workflows/" 666) nil
                                                      +test-api-key+ user-id)))))

        (testing "create default workflow with form"
          (let [form-id (test-helpers/create-form! {:form/internal-name "workflow form"
                                                    :form/external-title {:en "Workflow Form EN"
                                                                          :fi "Workflow Form FI"
                                                                          :sv "Workflow Form SV"}
                                                    :form/fields [{:field/type :text
                                                                   :field/title {:fi "fi" :sv "sv" :en "en"}
                                                                   :field/optional true}]})
                body (create-workflow user-id "organization1" :workflow/default [{:form/id form-id}])
                id (:id body)]
            (is (number? id))
            (testing "and fetch"
              (is (= (assoc-in expected [:workflow :forms] [{:form/id form-id
                                                             :form/internal-name "workflow form"
                                                             :form/external-title {:en "Workflow Form EN"
                                                                                   :fi "Workflow Form FI"
                                                                                   :sv "Workflow Form SV"}}])
                     (fetch +test-api-key+ user-id id))))))

        (testing "create default workflow with invalid form"
          (is (= {:success false
                  :errors [{:type "invalid-form" :forms [{:form/id 999999}]}]}
                 (create-workflow user-id "organization1" :workflow/default [{:form/id 999999}]))))

        (testing "create default workflow with invalid handlers"
          (is (= {:success false
                  :errors [{:type "invalid-user" :users ["nonexisting" "ghost"]}]}
                 (api-call :post "/api/workflows/create"
                           {:organization {:organization/id "organization1"}
                            :title "workflow title"
                            :type :workflow/default
                            :handlers ["handler" "nonexisting" "ghost"]}
                           +test-api-key+ user-id))))

        (testing "create decider workflow"
          (let [body (create-workflow user-id "organization1" :workflow/decider [])
                id (:id body)]
            (is (< 0 id))
            (testing "and fetch"
              (is (= (-> expected
                         (assoc-in [:workflow :type] "workflow/decider"))
                     (fetch +test-api-key+ user-id id))))))))

    (testing "create as organization-owner with incorrect organization"
      (let [response (api-response :post "/api/workflows/create"
                                   {:organization {:organization/id "organization2"}
                                    :title "workflow title"
                                    :type :workflow/default
                                    :handlers ["handler" "carl"]}
                                   +test-api-key+ "organization-owner1")]
        (is (response-is-forbidden? response))
        (is (= "no access to organization \"organization2\"" (read-body response)))))

    (doseq [user-id ["owner" "organization-owner1"]]
      (testing user-id
        (testing "list"
          (let [data (api-call :get "/api/workflows" nil
                               +test-api-key+ user-id)]
            (is (coll-is-not-empty? data))))))))

(deftest workflows-enabled-archived-test
  (create-handlers!)
  (let [user-id "owner"
        lic-id (test-helpers/create-license! {:organization {:organization/id "organization1"}})
        lic (-> (licenses/get-license lic-id)
                (replace-key :id :license/id))
        wfid (test-helpers/create-workflow! {:organization {:organization/id "organization1"}
                                             :title "workflow title"
                                             :type :workflow/default
                                             :handlers ["handler" "carl"]
                                             :licenses [lic-id]})

        fetch #(fetch +test-api-key+ user-id wfid)
        archive-license! #(with-user user-id
                            (licenses/set-license-archived! {:id lic-id
                                                             :archived %}))
        set-enabled! #(api-call :put "/api/workflows/enabled"
                                {:id wfid
                                 :enabled %1}
                                +test-api-key+ %2)
        set-archived! #(api-call :put "/api/workflows/archived"
                                 {:id wfid
                                  :archived %1}
                                 +test-api-key+ %2)]
    (testing "before changes"
      (is (= (-> expected
                 (assoc-in [:workflow :licenses] [lic]))
             (fetch))))
    (testing "as owner"
      (testing "disable and archive"
        (is (:success (set-enabled! false user-id)))
        (is (:success (set-archived! true user-id)))
        (is (= (-> expected
                   (assoc :enabled false
                          :archived true)
                   (assoc-in [:workflow :licenses] [lic]))
               (fetch))))
      (testing "re-enable"
        (is (:success (set-enabled! true user-id)))
        (is (= (-> expected
                   (assoc :archived true)
                   (assoc-in [:workflow :licenses] [lic]))
               (fetch))))
      (testing "unarchive"
        (is (:success (set-archived! false user-id)))
        (is (= (-> expected
                   (assoc-in [:workflow :licenses] [lic]))
               (fetch))))
      (testing "cannot unarchive if license is archived"
        (set-archived! true user-id)
        (archive-license! true)
        (is (not (:success (set-archived! false user-id))))
        (archive-license! false)
        (is (:success (set-archived! false user-id)))))
    (testing "as organization-owner"
      (is (:success (set-enabled! false "organization-owner1")))
      (is (:success (set-archived! true "organization-owner1")))
      (is (= (-> expected
                 (assoc :enabled false
                        :archived true)
                 (assoc-in [:workflow :licenses] [lic]))
             (fetch))))
    (testing "as owner of different organization"
      (is (response-is-forbidden? (api-response :put "/api/workflows/enabled"
                                                {:id wfid :enabled true}
                                                +test-api-key+ "organization-owner2")))
      (is (response-is-forbidden? (api-response :put "/api/workflows/archived"
                                                {:id wfid :archived false}
                                                +test-api-key+ "organization-owner2"))))))

(deftest workflows-edit-test
  (create-handlers!)
  (test-helpers/create-user! {:userid "tester"})
  (let [user-id "owner"
        wfid (test-helpers/create-workflow! {:organization {:organization/id "organization1"}
                                             :title "workflow title"
                                             :type :workflow/default
                                             :handlers ["handler" "carl"]})

        cat-id (test-helpers/create-catalogue-item! {:organization {:organization/id "organization1"}
                                                     :workflow-id wfid})
        app-id (test-helpers/create-application! {:catalogue-item-ids [cat-id]
                                                  :actor "tester"})
        application->handler-user-ids
        (fn [app] (set (mapv :userid (get-in app [:application/workflow :workflow.dynamic/handlers]))))]
    (testing "application is initialized with the correct set of handlers"
      (let [app (applications/get-application app-id)]
        (is (= #{"handler" "carl"}
               (application->handler-user-ids app)))))

    (testing "change organization, temporarily"
      (assert-success (api-call :put "/api/workflows/edit"
                                {:id wfid :organization {:organization/id "organization2"}}
                                +test-api-key+ user-id))
      (is (= (assoc expected
                    :organization {:organization/id "organization2"
                                   :organization/short-name {:en "ORG 2" :fi "ORG 2" :sv "ORG 2"}
                                   :organization/name {:en "Organization 2" :fi "Organization 2" :sv "Organization 2"}})
             (fetch +test-api-key+ user-id wfid)))
      (assert-success (api-call :put "/api/workflows/edit"
                                {:id wfid :organization {:organization/id "organization1"}}
                                +test-api-key+ user-id)))

    (testing "change title"
      (is (true? (:success (api-call :put "/api/workflows/edit"
                                     {:id wfid :title "x"}
                                     +test-api-key+ user-id))))
      (is (= (assoc expected
                    :title "x")
             (fetch +test-api-key+ user-id wfid))))

    (testing "change handlers"
      (is (true? (:success (api-call :put "/api/workflows/edit"
                                     {:id wfid :handlers ["owner" "alice"]}
                                     +test-api-key+ user-id))))
      (is (= (assoc expected
                    :title "x"
                    :workflow {:type "workflow/default"
                               :forms []
                               :handlers [{:email "owner@example.com"
                                           :name "Owner"
                                           :userid "owner"}
                                          {:email "alice@example.com"
                                           :name "Alice Applicant"
                                           :nickname "In Wonderland"
                                           :userid "alice"
                                           :organizations [{:organization/id "default"}]
                                           :researcher-status-by "so"}]
                               :licenses []})
             (fetch +test-api-key+ user-id wfid))))

    (testing "edit as organization-owner"
      (is (true? (:success (api-call :put "/api/workflows/edit"
                                     {:id wfid :title "y"}
                                     +test-api-key+ "organization-owner1"))))
      (is (= "y"
             (:title (fetch +test-api-key+ "organization-owner1" wfid)))))

    (testing "edit as owner of different organization"
      (is (response-is-forbidden? (-> (request :put "/api/workflows/edit")
                                      (json-body {:id wfid :title "y"})
                                      (authenticate +test-api-key+ "organization-owner2")
                                      handler))))

    (testing "application is updated when handlers are changed"
      (let [app (applications/get-application app-id)]
        (is (= #{"owner" "alice"}
               (application->handler-user-ids app)))))))

(deftest workflows-api-filtering-test
  (let [enabled-wf (test-helpers/create-workflow! {})
        disabled-wf (test-helpers/create-workflow! {})
        _ (with-user "owner"
            (workflows/set-workflow-enabled! {:id disabled-wf
                                              :enabled false}))
        enabled-and-disabled-wfs (set (map :id (-> (request :get "/api/workflows" {:disabled true})
                                                   (authenticate +test-api-key+ "owner")
                                                   handler
                                                   assert-response-is-ok
                                                   read-body)))
        enabled-wfs (set (map :id (-> (request :get "/api/workflows")
                                      (authenticate +test-api-key+ "owner")
                                      handler
                                      assert-response-is-ok
                                      read-body)))]
    (is (contains? enabled-and-disabled-wfs enabled-wf))
    (is (contains? enabled-and-disabled-wfs disabled-wf))
    (is (contains? enabled-wfs enabled-wf))
    (is (not (contains? enabled-wfs disabled-wf)))))

(deftest workflows-api-security-test
  (testing "without authentication"
    (testing "list"
      (let [response (-> (request :get (str "/api/workflows"))
                         handler)]
        (is (response-is-unauthorized? response))
        (is (= "unauthorized" (read-body response)))))
    (testing "create"
      (let [response (-> (request :post (str "/api/workflows/create"))
                         (json-body {:organization {:organization/id "test-organization"}
                                     :title "workflow title"
                                     :type :rounds
                                     :rounds [{:type :approval
                                               :actors ["handler"]}]})
                         handler)]
        (is (response-is-unauthorized? response))
        (is (= "Invalid anti-forgery token" (read-body response))))))

  (testing "without owner role"
    (testing "list"
      (let [response (-> (request :get (str "/api/workflows"))
                         (authenticate +test-api-key+ "alice")
                         handler)]
        (is (response-is-forbidden? response))
        (is (= "forbidden" (read-body response)))))
    (testing "create"
      (let [response (-> (request :post (str "/api/workflows/create"))
                         (json-body {:organization {:organization/id "test-organization"}
                                     :title "workflow title"
                                     :type :rounds
                                     :rounds [{:type :approval
                                               :actors ["handler"]}]})
                         (authenticate +test-api-key+ "alice")
                         handler)]
        (is (response-is-forbidden? response))
        (is (= "forbidden" (read-body response)))))))
