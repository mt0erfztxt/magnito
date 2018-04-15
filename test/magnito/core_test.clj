(ns magnito.core-test
  (:require
   [clojure.test :refer :all]
   [honeysql.core :as honeysql]
   [honeysql.helpers :as honeysql-helpers]
   [magnito.core :as magnito]))

(deftest build-cte-part-sql-map-left-join-condition-test
  (let [subj #'magnito/build-cte-part-sql-map-left-join-condition]
    (testing "010 It returs correct condition for left join when reverse evaluates to logical false"
      (let [result
            (subj
             [:root :profile]
             {:by [:profile :id]   ; implicitly set by resource/add-defaults
              :id :id
              :resourceType "Profile"
              :reverse false}
             {:id :id              ; implicitly set by resource/add-defaults
              :resourceType "Account"})]
        (is (= (honeysql/format
                (honeysql-helpers/left-join :table result))
               ["LEFT JOIN table ON jsonb_extract_path_text(t1.root, 'profile', 'id') = jsonb_extract_path_text(t2.resource, 'id')"]))))
    (testing "020 It returs correct condition for left join when reverse evaluates to logical true"
      (let [result
            (subj
             [:root :profile]
             {:by [:account :id]   ; implicitly set by resource/add-defaults
              :id :id
              :resourceType "Profile"
              :reverse true}
             {:id :id              ; implicitly set by resource/add-defaults
              :resourceType "Account"})]
        (is (= (honeysql/format
                (honeysql-helpers/left-join :table result))
               ["LEFT JOIN table ON jsonb_extract_path_text(t1.root, 'id') = jsonb_extract_path_text(t2.resource, 'account', 'id')"]))))))

(deftest build-cte-part-sql-map-left-join-test
  (let [subj #'magnito/build-cte-part-sql-map-left-join]
    (testing "010 It returns new version of passed in SQL map without LEFT JOIN clause added when resource is root"
      (is (= (honeysql/format
              (subj
               {:select [:*] :from [[:account :t1]]}
               [:root]
               {:id :id              ; implicitly set by resource/add-defaults
                :resourceType "Account"
                :root? true}
               nil))
             ["SELECT * FROM account t1"])))
    (testing "020 It returns new version of passed in SQL map with LEFT JOIN clause added when resource is not root"
      (is (= (honeysql/format
              (subj
               {:select [:*] :from [[:account :t1]]}
               [:root :profile]
               {:by [:profile :id]   ; implicitly set by resource/add-defaults
                :id :id
                :resourceType "Profile"
                :reverse false}
               {:by nil              ; implicitly set by resource/add-defaults
                :id :id              ; implicitly set by resource/add-defaults
                :resourceType "Account"}))
             ["SELECT * FROM account t1 LEFT JOIN Profile t2 ON jsonb_extract_path_text(t1.root, 'profile', 'id') = jsonb_extract_path_text(t2.resource, 'id')"])))))

(deftest build-cte-part-sql-map-from-test
  (let [subj #'magnito/build-cte-part-sql-map-from]
    (testing "010 It returns new version of passed in SQL map with FROM clause added"
      ;; Case of root resource.
      (is (= (honeysql/format
              (subj
               {:select [:*]}
               [:root]
               {:resourceType "Account" :root? true}))
             ["SELECT * FROM Account t1"]))
      ;; Case of non-root resource.
      (is (= (honeysql/format
              (subj
               {:select [:*]}
               [:root :profile]
               {:resourceType "Profile" :root? false}))
             ["SELECT * FROM _root t1"])))))

(deftest build-cte-part-sql-map-select-test
  (let [subj #'magnito/build-cte-part-sql-map-select]
    (testing "010 It returns new version of passed in SQL map with SELECT clause added (root case)"
      (is (= (honeysql/format
              (subj
               {:from [[:table :t1]]}
               [:account]
               {:resourceType "Account" :root? true}))
             ["SELECT t1.resource AS account FROM table t1"])))
    (testing "020 It returns new version of passed in SQL map with SELECT clause added (non-root case)"
      (is (= (honeysql/format
              (subj
               {:from [[:table :t1]]}
               [:account :profile]
               {:resourceType "profile" :root? false}))
             ["SELECT t2.resource AS profile FROM table t1"])))))

(deftest build-cte-part-sql-map-test
  (let [subj #'magnito/build-cte-part-sql-map]
    (testing "010 It returns accumulator with new CTE added (root case)"
      (let [[cte-part-name cte-part-body :as result]
            (subj
             {:cte-path [:account] :sql-ctes []}
             {:resourceType "Account" :root? true}
             nil)]
        (is (= cte-part-name :_account))
        (is (= (honeysql/format
                (honeysql-helpers/with result))
               ["WITH _account AS (SELECT t1.resource AS account FROM Account t1)"]))))
    (testing "020 It returns accumulator with new CTE added (non-root, non-reverse case)"
      (let [[cte-part-name cte-part-body :as result]
            (subj
             {:cte-path [:account :profile] :sql-ctes []}
             {:id :id
              :by [:profile :id]
              :resourceType "Profile"
              :reverse false
              :root? false}
             {:id :id
              :resourceType "Account"
              :root? true})]
        (is (= cte-part-name :_account_profile))
        (is (= (honeysql/format
                (honeysql-helpers/with result))
               ["WITH _account_profile AS (SELECT t2.resource AS profile FROM _account t1 LEFT JOIN Profile t2 ON jsonb_extract_path_text(t1.account, 'profile', 'id') = jsonb_extract_path_text(t2.resource, 'id'))"]))))
    (testing "030 It returns accumulator with new CTE added (non-root, reverse case)"
      (let [[cte-part-name cte-part-body :as result]
            (subj
             {:cte-path [:account :posts] :sql-ctes []}
             {:id :id
              :by [:author :id]
              :resourceType "Post"
              :reverse true
              :root? false}
             {:id :id
              :resourceType "Account"
              :root? true})]
        (is (= cte-part-name :_account_posts))
        (is (= (honeysql/format
                (honeysql-helpers/with result))
               ["WITH _account_posts AS (SELECT t2.resource AS posts FROM _account t1 LEFT JOIN Post t2 ON jsonb_extract_path_text(t1.account, 'id') = jsonb_extract_path_text(t2.resource, 'author', 'id'))"]))))))

(deftest build-query-part-sql-map-left-join-condition-test
  (let [subj #'magnito/build-query-part-sql-map-left-join-condition]
    (testing "010 It returs correct condition for left join when reverse evaluates to logical false"
      (let [result
            (subj
             :t1.account
             :t2.profile
             {:id :id              ; implicitly set by resource/add-defaults
              :resourceType "Account"}
             {:by [:profile :id]   ; implicitly set by resource/add-defaults
              :id :id
              :resourceType "Profile"
              :reverse false})]
        (is (= (honeysql/format
                (honeysql-helpers/left-join :table result))
               ["LEFT JOIN table ON jsonb_extract_path_text(t1.account, 'profile', 'id') = jsonb_extract_path_text(t2.profile, 'id')"]))))
    (testing "020 It returs correct condition for left join when value of :reverse of referenced resource evaluates to logical true"
      (let [result
            (subj
             :t1.account
             :t2.profile
             {:id :id              ; implicitly set by resource/add-defaults
              :resourceType "Account"}
             {:by [:account :id]   ; explicitly set
              :id :id
              :resourceType "Profile"
              :reverse true})]
        (is (= (honeysql/format
                (honeysql-helpers/left-join :table result))
               ["LEFT JOIN table ON jsonb_extract_path_text(t1.account, 'id') = jsonb_extract_path_text(t2.profile, 'account', 'id')"]))))
    (testing "030 It returs correct condition for left join when value of :collection of referenced resource evaluates to logical true"
      (let [result
            (subj
             :t1.account
             :t2.posts
             {:id :id             ; implicitly set by resource/add-defaults
              :resourceType "Account"}
             {:by [:author :id]   ; explicitly set
              :collection true
              :id :id
              :resourceType "Post"
              :reverse true})]
        (is (= (honeysql/format
                (honeysql-helpers/left-join :table result))
               ["LEFT JOIN table ON jsonb_extract_path_text(t1.account, 'id') = jsonb_extract_path_text(t2.posts, '0', 'author', 'id')"]))))
    (testing "040 It takes into account value of :id of passed in resources"
      (let [result
            (subj
             :t1.account
             :t2.profile
             {:id :id              ; implicitly set by resource/add-defaults
              :resourceType "Account"}
             {:by [:profile :id]   ; implicitly set by resource/add-defaults
              :id :custom-id       ; explicitly set
              :resourceType "Profile"
              :reverse false})]
        (is (= (honeysql/format
                (honeysql-helpers/left-join :table result))
               ["LEFT JOIN table ON jsonb_extract_path_text(t1.account, 'profile', 'id') = jsonb_extract_path_text(t2.profile, 'custom_id')"])))
      (let [result
            (subj
             :t1.account
             :t2.profile
             {:id :custom-id       ; explicitly set
              :resourceType "Account"}
             {:by [:profile :id]   ; explicitly set
              :id :custom-id       ; explicitly set, but ignored because of :by also explicitly set
              :resourceType "Profile"
              :reverse false})]
        (is (= (honeysql/format
                (honeysql-helpers/left-join :table result))
               ["LEFT JOIN table ON jsonb_extract_path_text(t1.account, 'profile', 'id') = jsonb_extract_path_text(t2.profile, 'custom_id')"])))
      (let [result
            (subj
             :t1.account
             :t2.posts
             {:id :id                    ; implicitly set by resource/add-defaults
              :resourceType "Account"}
             {:by [:author :custom-id]   ; explicitly set
              :collection true
              :resourceType "Post"
              :reverse true})]
        (is (= (honeysql/format
                (honeysql-helpers/left-join :table result))
               ["LEFT JOIN table ON jsonb_extract_path_text(t1.account, 'id') = jsonb_extract_path_text(t2.posts, '0', 'author', 'custom_id')"]))))))

(deftest build-query-part-sql-map-left-join-test
  (let [subj #'magnito/build-query-part-sql-map-left-join]
    (testing "010 It returns new version of passed in SQL map with LEFT JOIN clause added"
      (is (= (honeysql/format
              (subj
               {:select [:*] :from [[:account :t1]]}
               :account_profile
               :t1.account
               :t2.profile
               {:id :id              ; implicitly set by resource/add-defaults
                :resourceType "Account"}
               {:by [:profile :id]   ; implicitly set by resource/add-defaults
                :id :id
                :resourceType "Profile"
                :reverse false}))
             ["SELECT * FROM account t1 LEFT JOIN account_profile t2 ON jsonb_extract_path_text(t1.account, 'profile', 'id') = jsonb_extract_path_text(t2.profile, 'id')"])))))

(deftest build-query-part-sql-map-from-test
  (let [subj #'magnito/build-query-part-sql-map-from]
    (testing "010 It returns new version of passed in SQL map with FROM clause added"
      (is (= (honeysql/format
              (subj
               {:select [:*]}
               :root_something_otherthing))
             ["SELECT * FROM root_something_otherthing t1"]))
      (is (= (honeysql/format
              (subj
               {:select [:*]}
               {:select [:a] :from [:b]}))
             ["SELECT * FROM (SELECT a FROM b) t1"])))))

(deftest build-query-part-sql-map-select-test
  (let [subj #'magnito/build-query-part-sql-map-select]
    (testing "010 It returns new version of passed in SQL map with SELECT clause added"
      (is (= (honeysql/format
              (subj {:from [[:table :t1]]} :account :t1.account :t2.profile :profile))
             ["SELECT CASE WHEN t2.profile IS NULL THEN t1.account ELSE jsonb_set(t1.account, '{profile}', t2.profile) END AS account FROM table t1"])))))

(deftest build-query-part-sql-map-test
  (let [subj #'magnito/build-query-part-sql-map]
    (testing "010 It returns accumulator with SQL query updated (resource without references case)"
      (is (= (honeysql/format
              (subj
               [:account]
               nil
               :_account
               {:by nil
                :id :id
                :resourceType "Account"
                :root? true}
               nil
               nil))
             ["SELECT t1.account FROM _account t1"])))
    (testing "020 It returns accumulator with SQL query updated (resource with references case)"
      (is (= (honeysql/format
              (subj
               [:account]
               :profile
               :_account
               {:by nil
                :id :id
                :resourceType "Account"
                :root? true}
               :_account_profile
               {:by [:profile :id]
                :id :id
                :resourceType "Profile"
                :reverse false
                :root? true}))
             ["SELECT CASE WHEN t2.profile IS NULL THEN t1.account ELSE jsonb_set(t1.account, '{profile}', t2.profile) END AS account FROM _account t1 LEFT JOIN _account_profile t2 ON jsonb_extract_path_text(t1.account, 'profile', 'id') = jsonb_extract_path_text(t2.profile, 'id')"])))
    (testing "030 It allows t1 and t2 to be sub-queries"
      (is (= (honeysql/format
              (subj
               [:account]
               :profile
               {:select [:*] :from [:a]}
               {:by nil
                :id :id
                :resourceType "Account"
                :root? true}
               {:select [:*] :from [:b]}
               {:by [:profile :id]
                :id :id
                :resourceType "Profile"
                :reverse false
                :root? true}))
             ["SELECT CASE WHEN t2.profile IS NULL THEN t1.account ELSE jsonb_set(t1.account, '{profile}', t2.profile) END AS account FROM (SELECT * FROM a) t1 LEFT JOIN (SELECT * FROM b) t2 ON jsonb_extract_path_text(t1.account, 'profile', 'id') = jsonb_extract_path_text(t2.profile, 'id')"])))))

(deftest process-resource-build-query-part-aggregate-collection-test
  (let [subj #'magnito/process-resource-build-query-part-wrap-in-aggregate]
    (testing "010 It aggregates src when it's a keyword (name of CTE)"
      (is (= (honeysql/format
              (subj
               :account_posts
               [:account :posts]
               {:by [:author :id]
                :collection true
                :id :id
                :resourceType "Posts"
                :reverse true
                :root? :false}))
             ["SELECT jsonb_agg(t1.posts) AS posts FROM account_posts t1 GROUP BY jsonb_extract_path_text(t1.posts, 'author', 'id') HAVING jsonb_extract_path_text(t1.posts, 'author', 'id') IS NOT NULL"])))
    (testing "020 It aggregates src when it's a map (some SQL build somehow)"
      (is (= (honeysql/format
              (subj
               {:select [:*] :from [:table]}
               [:account :posts]
               {:by [:author :id]
                :collection true
                :id :id
                :resourceType "Posts"
                :reverse true
                :root? :false}))
             ["SELECT jsonb_agg(t1.posts) AS posts FROM (SELECT * FROM table) t1 GROUP BY jsonb_extract_path_text(t1.posts, 'author', 'id') HAVING jsonb_extract_path_text(t1.posts, 'author', 'id') IS NOT NULL"])))))
