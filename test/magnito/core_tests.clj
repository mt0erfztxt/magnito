(ns magnito.core-tests
  (:require
   [clojure.java.jdbc :as jdbc]
   [clojure.test :refer :all]
   [magnito.core :as magnito]
   [magnito.fixtures :as fixtures]))

(use-fixtures :once fixtures/wrap-setup)

(deftest it-returns-correct-result
  (testing "010 It returns correct result for 1-level resource tree"
    (let [resource {:resourceType "Account"}
          result
          (->> resource
               (magnito/resource->sql)
               (jdbc/query fixtures/db-spec))]
      (is (= (-> result (first) (:result))
             [{:resourceType "Account"
               :id "account-1"
               :name "Account 1"
               :profile {:id "profile-1"}}
              {:resourceType "Account"
               :id "account-2"
               :name "Account 2"
               :profile {:id "profile-2"}}
              {:resourceType "Account"
               :id "account-3"
               :name "Account 3"
               :info "No profile"}
              {:resourceType "Account"
               :id "account-4"
               :name "Account 4"
               :info "No profile, No Posts, No Comments"}]))))
  (testing "020 It returns correct result for 2-level resource tree"
    (let [resource
          {:resourceType "Account"
           :references
           {:posts
            {:resourceType "Post"
             :by [:author :id]
             :collection true
             :reverse true}}}
          result
          (->> resource
               (magnito/resource->sql)
               (jdbc/query fixtures/db-spec))]
      (is (= (-> result (first) (:result))
             [{:resourceType "Account"
               :id "account-1"
               :name "Account 1"
               :profile {:id "profile-1"}
               :posts
               [{:resourceType "Post" :id "post-1" :author {:id "account-1"} :text "Post 1 text"}
                {:resourceType "Post" :id "post-4" :author {:id "account-1"} :text "Post 4 text"}]}
              {:resourceType "Account"
               :id "account-2"
               :name "Account 2"
               :profile {:id "profile-2"}
               :posts
               [{:resourceType "Post" :id "post-3" :author {:id "account-2"} :text "Post 3 text"}
                {:resourceType "Post" :id "post-5" :author {:id "account-2"} :text "Post 5 text"}]}
              {:resourceType "Account"
               :id "account-3"
               :name "Account 3"
               :info "No profile"
               :posts
               [{:resourceType "Post" :id "post-2" :author {:id "account-3"} :text "Post 2 text"}]}
              {:resourceType "Account"
               :id "account-4"
               :name "Account 4"
               :info "No profile, No Posts, No Comments"}]))))
  (testing "030 It returns correct result for 3-level resource tree"
    (let [resource
          {:resourceType "Account"
           :references
           {:posts
            {:resourceType "Post"
             :by [:author :id]
             :collection true
             :reverse true
             :references
             {:commentaries
              {:resourceType "Commentary"
               :by [:post :id]
               :collection true
               :reverse true}}}}}
          result
          (->> resource
               (magnito/resource->sql)
               (jdbc/query fixtures/db-spec))]
      (is (= (-> result (first) (:result))
             [{:resourceType "Account"
               :id "account-1"
               :name "Account 1"
               :profile {:id "profile-1"}
               :posts
               [{:resourceType "Post"
                 :id "post-1"
                 :author {:id "account-1"}
                 :text "Post 1 text"}
                {:resourceType "Post"
                 :id "post-4"
                 :author {:id "account-1"}
                 :text "Post 4 text"
                 :commentaries
                 [{:resourceType "Commentary"
                   :id "commentary-1"
                   :post {:id "post-4"}
                   :text "Comment 1"
                   :author {:id "account-2"}}
                  {:resourceType "Commentary"
                   :id "commentary-2"
                   :post {:id "post-4"}
                   :text "Comment 2"
                   :author {:id "account-3"}}
                  {:resourceType "Commentary"
                   :id "commentary-5"
                   :post {:id "post-4"}
                   :text "Comment 5"
                   :author {:id "account-1"}}]}]}
              {:resourceType "Account"
               :id "account-2"
               :name "Account 2"
               :profile {:id "profile-2"}
               :posts
               [{:resourceType "Post"
                 :id "post-3"
                 :author {:id "account-2"}
                 :text "Post 3 text"}
                {:resourceType "Post"
                 :id "post-5"
                 :author {:id "account-2"}
                 :text "Post 5 text"
                 :commentaries
                 [{:resourceType "Commentary"
                   :id "commentary-4"
                   :post {:id "post-5"}
                   :text "Comment 4"
                   :author {:id "account-1"}}]}]}
              {:resourceType "Account"
               :id "account-3"
               :name "Account 3"
               :info "No profile"
               :posts
               [{:resourceType "Post"
                 :id "post-2"
                 :author {:id "account-3"}
                 :text "Post 2 text"
                 :commentaries
                 [{:resourceType "Commentary"
                   :id "commentary-3"
                   :post {:id "post-2"}
                   :text "Comment 3"
                   :author {:id "account-1"}}
                  {:resourceType "Commentary"
                   :id "commentary-6"
                   :post {:id "post-2"}
                   :text "Comment 6"
                   :author {:id "account-2"}}]}]}
              {:resourceType "Account"
               :id "account-4"
               :name "Account 4"
               :info "No profile, No Posts, No Comments"}]))))
  (testing "040 It returns correct result for 4-level resource tree"
    (let [resource
          {:resourceType "Account"
           :references
           {:posts
            {:resourceType "Post"
             :by [:author :id]
             :collection true
             :reverse true
             :references
             {:commentaries
              {:resourceType "Commentary"
               :by [:post :id]
               :collection true
               :reverse true
               :references
               {:author
                {:resourceType "Account"}}}}}}}
          result
          (->> resource
               (magnito/resource->sql)
               (jdbc/query fixtures/db-spec))]
      (is (= (-> result (first) (:result))
             [{:resourceType "Account"
               :id "account-1"
               :name "Account 1"
               :profile {:id "profile-1"}
               :posts
               [{:resourceType "Post"
                 :id "post-1"
                 :author {:id "account-1"}
                 :text "Post 1 text"}
                {:resourceType "Post"
                 :id "post-4"
                 :author {:id "account-1"}
                 :text "Post 4 text"
                 :commentaries
                 [{:resourceType "Commentary"
                   :id "commentary-1"
                   :post {:id "post-4"}
                   :text "Comment 1"
                   :author
                   {:resourceType "Account"
                    :id "account-2"
                    :name "Account 2"
                    :profile {:id "profile-2"}}}
                  {:resourceType "Commentary"
                   :id "commentary-2"
                   :post {:id "post-4"}
                   :text "Comment 2"
                   :author
                   {:resourceType "Account"
                    :id "account-3"
                    :name "Account 3"
                    :info "No profile"}}
                  {:resourceType "Commentary"
                   :id "commentary-5"
                   :post {:id "post-4"}
                   :text "Comment 5"
                   :author
                   {:resourceType "Account"
                    :id "account-1"
                    :name "Account 1"
                    :profile {:id "profile-1"}}}]}]}
              {:resourceType "Account"
               :id "account-2"
               :name "Account 2"
               :profile {:id "profile-2"}
               :posts
               [{:resourceType "Post"
                 :id "post-3"
                 :author {:id "account-2"}
                 :text "Post 3 text"}
                {:resourceType "Post"
                 :id "post-5"
                 :author {:id "account-2"}
                 :text "Post 5 text"
                 :commentaries
                 [{:resourceType "Commentary"
                   :id "commentary-4"
                   :post {:id "post-5"}
                   :text "Comment 4"
                   :author
                   {:resourceType "Account"
                    :id "account-1"
                    :name "Account 1"
                    :profile {:id "profile-1"}}}]}]}
              {:resourceType "Account"
               :id "account-3"
               :name "Account 3"
               :info "No profile"
               :posts
               [{:resourceType "Post"
                 :id "post-2"
                 :author {:id "account-3"}
                 :text "Post 2 text"
                 :commentaries
                 [{:resourceType "Commentary"
                   :id "commentary-3"
                   :post {:id "post-2"}
                   :text "Comment 3"
                   :author
                   {:resourceType "Account"
                    :id "account-1"
                    :name "Account 1"
                    :profile {:id "profile-1"}}}
                  {:resourceType "Commentary"
                   :id "commentary-6"
                   :post {:id "post-2"}
                   :text "Comment 6"
                   :author
                   {:resourceType "Account"
                    :id "account-2"
                    :name "Account 2"
                    :profile {:id "profile-2"}}}]}]}
              {:resourceType "Account"
               :id "account-4"
               :name "Account 4"
               :info "No profile, No Posts, No Comments"}])))))
