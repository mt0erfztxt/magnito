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
               ["SELECT json_agg(t1.posts) AS posts FROM ? t1 GROUP BY jsonb_extract_path_text(t1.posts, ?, ?) HAVING jsonb_extract_path_text(t1.posts, ?, ?) IS NOT NULL"
                "root_posts"
                "'author'"
                "'id'"
                "'author'"
                "'id'"])))
      (let [result (subj {:select [:a] :from [:b]} :posts {:by [:author :id]})]
        (is (map? result))
        (is (= (honeysql/format result)
               ["SELECT json_agg(t1.posts) AS posts FROM (SELECT a FROM b) t1 GROUP BY jsonb_extract_path_text(t1.posts, ?, ?) HAVING jsonb_extract_path_text(t1.posts, ?, ?) IS NOT NULL"
                "'author'"
                "'id'"
                "'author'"
                "'id'"]))))))
