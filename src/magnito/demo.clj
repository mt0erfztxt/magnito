(ns magnito.demo
  (:require
   [cheshire.core :as json]
   [clojure.java.jdbc :as jdbc]
   [magnito.core :as magnito])
  (:import
   [java.sql PreparedStatement]
   clojure.lang.IPersistentMap
   clojure.lang.IPersistentVector
   java.sql.Array
   org.postgresql.util.PGobject))

(def db-spec
  (System/getenv "DATABASE_URL"))

(defn- to-pg-json [value]
  (doto (PGobject.)
    (.setType "jsonb")
    (.setValue (json/generate-string value))))

(extend-protocol jdbc/IResultSetReadColumn
  Array
  (result-set-read-column [v _ _] (vec (.getArray v)))
  PGobject
  (result-set-read-column [pgobj _metadata _index]
    (let [type  (.getType pgobj)
          value (.getValue pgobj)]
      (case type
        "json" (json/parse-string value true)
        "jsonb" (json/parse-string value true)
        "citext" (str value)
        value))))

(extend-type IPersistentVector
  jdbc/ISQLParameter
  (set-parameter [v ^PreparedStatement stmt ^long idx]
    (let [conn      (.getConnection stmt)
          meta      (.getParameterMetaData stmt)
          type-name (.getParameterTypeName meta idx)]
      (if-let [elem-type (when (= (first type-name) \_) (apply str (rest type-name)))]
        (.setObject stmt idx (.createArrayOf conn elem-type (to-array v)))
        (.setObject stmt idx (to-pg-json v))))))

(extend-protocol jdbc/ISQLValue
  IPersistentMap
  (sql-value [value] (to-pg-json value))
  IPersistentVector
  (sql-value [value] (to-pg-json value)))

(defn- prepare-db!-create-tables
  [t-con]
  (jdbc/db-do-commands
   t-con
   (for [entity-name [:Account :Commentary :Post :Profile]]
     (jdbc/create-table-ddl
      entity-name
      [[:id :text "PRIMARY KEY"]
       [:resource :jsonb]]
      {:conditional? true
       :transaction? false}))))

(defn- prepare-db!-truncate-tables
  [t-con]
  (jdbc/execute!
   t-con
   "TRUNCATE TABLE Account, Commentary, Post, Profile"))

(defn- prepare-db!-insert-data
  [t-con]
  (jdbc/insert-multi!
   t-con
   :Account
   [{:id "account-1"
     :resource {:resourceType "Account" :id "account-1" :name "Account 1" :profile {:id "profile-1"}}}
    {:id "account-2"
     :resource {:resourceType "Account" :id "account-2" :name "Account 2" :profile {:id "profile-2"}}}
    {:id "account-3"
     :resource {:resourceType "Account" :id "account-3" :name "Account 3" :info "No profile"}}
    {:id "account-4"
     :resource {:resourceType "Account" :id "account-4" :name "Account 4" :info "No profile, No Posts"}}]
   {:transaction? false})
  (jdbc/insert-multi!
   t-con
   :Profile
   [{:id "profile-1"
     :resource {:resourceType "Profile" :id "profile-1" :enabled false :other "foo"}}
    {:id "profile-2"
     :resource {:resourceType "Profile" :id "profile-2" :enabled true :other "bar"}}])
  (jdbc/insert-multi!
   t-con
   :Post
   [{:id "post-11"
     :resource {:resourceType "Post" :id "post-11" :author {:id "account-1"} :text "Post 11 text"}}
    {:id "post-12"
     :resource {:resourceType "Post" :id "post-12" :author {:id "account-1"} :text "Post 12 text"}}
    {:id "post-21"
     :resource {:resourceType "Post" :id "post-21" :author {:id "account-2"} :text "Post 21 text"}}
    {:id "post-22"
     :resource {:resourceType "Post" :id "post-22" :author {:id "account-2"} :text "Post 22 text"}}
    {:id "post-31"
     :resource {:resourceType "Post" :id "post-31" :author {:id "account-3"} :text "Post 31 text"}}])
  (jdbc/insert-multi!
   t-con
   :Commentary
   [{:id "commentary-115"
     :resource {:resourceType "Commentary" :id "commentary-115" :post {:id "post-11"} :text "Comment 115" :author {:id "account-2"}}}
    {:id "commentary-118"
     :resource {:resourceType "Commentary" :id "commentary-118" :post {:id "post-11"} :text "Comment 118" :author {:id "account-1"}}}
    {:id "commentary-119"
     :resource {:resourceType "Commentary" :id "commentary-119" :post {:id "post-11"} :text "Comment 119" :author {:id "account-1"}}}
    {:id "commentary-212"
     :resource {:resourceType "Commentary" :id "commentary-212" :post {:id "post-21"} :text "Comment 212" :author {:id "account-1"}}}
    {:id "commentary-220"
     :resource {:resourceType "Commentary" :id "commentary-220" :post {:id "post-22"} :text "Comment 220" :author {:id "account-2"}}}
    {:id "commentary-315"
     :resource {:resourceType "Commentary" :id "commentary-315" :post {:id "post-31"} :text "Comment 312" :author {:id "account-3"}}}])
  nil)

(defn- prepare-db!
  [db-spec]
  (jdbc/with-db-transaction [t-con db-spec]
    (do
      (prepare-db!-create-tables t-con)
      (prepare-db!-truncate-tables t-con)
      (prepare-db!-insert-data t-con))))

(defn run
  []
  (prepare-db! db-spec)
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
        sql-str (magnito/resource->sql resource {:str? true})
        sql-vec (magnito/resource->sql resource)]
    (println "###############################################################################")
    (println "#                                  Resource                                   #")
    (println "###############################################################################")
    (clojure.pprint/pprint resource)
    (println "###############################################################################")
    (println "#                                  SQL (vec)                                  #")
    (println "###############################################################################")
    (clojure.pprint/pprint sql-vec)
    (println "###############################################################################")
    (println "#                                  SQL (str)                                  #")
    (println "###############################################################################")
    (clojure.pprint/pprint sql-str)
    (println "###############################################################################")
    (println "#                                   Result                                    #")
    (println "###############################################################################")
    (clojure.pprint/pprint
     (jdbc/query db-spec sql-vec))))
