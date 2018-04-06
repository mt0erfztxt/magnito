(ns magnito.core-test
  (:require
   [clojure.test :refer :all]
   [honeysql.format :as honeysql-format]
   [magnito.core :as magnito]))

(def query
  {:resourceType "User"
   :references
   {:profile
    {:resourceType "Profile"
     :elements [:name]}
    #_:posts
    #_{:resourceType "Post"
       :reverse true
       :collection true
       :by [:author :id]
       :elements [:title] ;; pick only required attributes
       :references
       {:comments
        {:resourceType "Comment"
         :by [:post :id]
         :collection true
         :reverse true
         :references
         {:author
          {:resourceType "User"}}}}}}})

(deftest build-sql-test
  (testing "It builds SQL"
    (is (= 0 1))))
