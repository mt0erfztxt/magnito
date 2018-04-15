(ns magnito.resource-test
  (:require
   [clojure.test :refer :all]
   [magnito.resource :as resource]))

(deftest add-defaults-test
  (let [subj resource/add-defaults]
    (testing "010 It sets :collection to false when it's not explicitly set"
      (is (false? (:collection (subj {:root? true} nil [:root]))))
      (is (= "foo" (:collection (subj {:collection "foo" :root? true} nil [:root])))))
    (testing "020 It sets :elements to [:*] when it's not explicitly set"
      (is (= [:*] (:elements (subj {:root? true} nil [:root]))))
      (is (= "foo" (:elements (subj {:elements "foo" :root? true} nil [:root])))))
    (testing "030 It sets :reverse to false when it's not explicitly set"
      (is (false? (:reverse (subj {:root? true} nil [:root]))))
      (is (= "foo" (:reverse (subj {:reverse "foo" :root? true} nil [:root])))))
    (testing "040 It sets :id to :id when it's not explicitly set"
      (is (= :id (:id (subj {:root? true} nil [:root]))))
      (is (= :custom-id (:id (subj {:id :custom-id :root? true} nil [:root])))))
    (testing "050 It sets :by to nil for root resource"
      (is (nil? (:by (subj {:by [:foo :bar] :root? true} nil [:root])))))
    (testing "060 It preserves :by when it's explicitly set"
      (is (= [:foo :bar] (:by (subj {:by [:foo :bar] :root? false} {} [:root :profile])))))
    (testing "070 It sets :by to [parent-resource-type-keyword (:id parent-resource)] when it's not explicitly set and :reverse evaluates to logical true"
      (is (= [:account :id]
             (:by
              (subj
               {:resourceType "Profile" :reverse true :root? false}
               {:resourceType "Account" :id :id}
               [:root :profile]))))
      (is (= [:account :custom-id]
             (:by
              (subj
               {:resourceType "Profile" :reverse true :root? false}
               {:resourceType "Account" :id :custom-id}
               :profile)))))
    (testing "080 It sets :by to [ref-key (:id resource)] when it's not explicitly set and :reverse evaluates to logical false"
      (is (= [:profile :id]
             (:by
              (subj
               {:resourceType "Profile" :reverse false :root? false}
               {:resourceType "Account"}
               :profile))))
      (is (= [:profile :custom-id]
             (:by
              (subj
               {:resourceType "Profile" :reverse false :id :custom-id :root? false}
               {:resourceType "Account"}
               :profile)))))
    (testing "090 It sets :root to false when it's not true"
      (is (true? (:root? (subj {:root? true} nil [:root]))))
      (is (false? (:root? (subj {:root? nil} {} [:root :profile])))))))
