(ns magnito.core-test
  (:require
   [clojure.test :refer :all]
   [honeysql.core :as honeysql]
   [magnito.core :as magnito]))

(deftest build-query-part-aggregate-collection-resource-test
  (testing "It returns correct SQL"
    (let [subj #'magnito/build-query-part-aggregate-collection-resource]
      (let [result (subj [:root] :posts {:by [:author :id]})]
        (is (map? result))
        (is (= (honeysql/format result)
               ["SELECT jsonb_agg(t1.posts) AS posts FROM root_posts t1 GROUP BY jsonb_extract_path_text(t1.posts, 'author', 'id') HAVING jsonb_extract_path_text(t1.posts, 'author', 'id') IS NOT NULL"])))
      (let [result (subj {:select [:a] :from [:b]} :posts {:by [:author :id]})]
        (is (map? result))
        (is (= (honeysql/format result)
               ["SELECT jsonb_agg(t1.posts) AS posts FROM (SELECT a FROM b) t1 GROUP BY jsonb_extract_path_text(t1.posts, 'author', 'id') HAVING jsonb_extract_path_text(t1.posts, 'author', 'id') IS NOT NULL"]))))))
