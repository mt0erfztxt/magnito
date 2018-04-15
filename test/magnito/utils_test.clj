(ns magnito.utils-test
  (:require
   [clojure.test :refer :all]
   [honeysql.core :as honeysql]
   [magnito.utils :as utils]))

(defn- to-sql-json-attr-test-check-is-thrown-on-invalid-input
  [v]
  (is
   (thrown-with-msg?
    IllegalArgumentException
    #"argument must be a keyword, non-blank string or number"
    (utils/to-sql-json-attr v))))

(deftest to-sql-json-attr-test
  (let [subj utils/to-sql-json-attr
        result (keyword "'id'")]
    (testing "010 It throws on incorrect input"
      (to-sql-json-attr-test-check-is-thrown-on-invalid-input true)
      (to-sql-json-attr-test-check-is-thrown-on-invalid-input "")
      (to-sql-json-attr-test-check-is-thrown-on-invalid-input "   "))
    (testing "020 It returns correct value when argument is a keyword"
      (is (= result (subj :id))))
    (testing "020 It returns correct value when argument is a non-blank string"
      (is (= result (subj "id"))))
    (testing "030 It returns correct value when argument is a number"
      (is (= (keyword "'0'") (subj 0))))))

(deftest call-jsonb-extract-path-text-sql-func-test
  (let [subj utils/call-jsonb-extract-path-text-sql-func]
    (testing "010 It returns correct result when field is a keyword"
      (is (= (subj :field :a "b")
             #sql/call [:jsonb_extract_path_text :field #=(keyword "'a'") #=(keyword "'b'")])))
    (testing "020 It returns correct result when field is a string"
      (is (= (subj "field" :a "b")
             #sql/call [:jsonb_extract_path_text :field #=(keyword "'a'") #=(keyword "'b'")])))
    (testing "030 It ignores nil path segments"
      (is (= (subj "field" :a "b" nil :c)
             #sql/call [:jsonb_extract_path_text :field #=(keyword "'a'") #=(keyword "'b'") #=(keyword "'c'")])))))

(deftest call-jsonb-set-sql-func-test
  (let [subj utils/call-jsonb-set-sql-func]
    (testing "010 It returns correct result"
      (is (= (honeysql/format (subj :t1.field1 [:a :b] :t2.field2))
             (honeysql/format (subj :t1.field1 ["a" :b] :t2.field2))
             ["jsonb_set(t1.field1, '{a,b}', t2.field2)"])))
    (testing "020 It allows path to be a keyword"
      (is (= (honeysql/format (subj :t1.field1 :a :t2.field2))
             ["jsonb_set(t1.field1, '{a}', t2.field2)"])))
    (testing "030 It allows path to be a string"
      (is (= (honeysql/format (subj :t1.field1 "b" :t2.field2))
             ["jsonb_set(t1.field1, '{b}', t2.field2)"])))))

(deftest unqualify-field-test
  (let [subj utils/unqualify-field]
    (testing "010 It returns unqualified field when passed in argument is a qualified field string"
      (is (= (subj "t1.field") :field)))
    (testing "020 It returns unqualified field when passed in argument is a qualified field keyword"
      (is (= (subj :t1.a) :a)))
    (testing "030 It returns unqualified field when passed in argument is an unqualified field string"
      (is (= (subj "field") :field)))
    (testing "040 It returns unqualified field when passed in argument is an unqualified field keyword"
      (is (= (subj :field-a) :field-a)))))
