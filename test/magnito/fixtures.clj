(ns magnito.fixtures
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
     :resource {:resourceType "Account" :id "account-4" :name "Account 4" :info "No profile, No Posts, No Comments"}}]
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
   [{:id "post-1"
     :resource {:resourceType "Post" :id "post-1" :author {:id "account-1"} :text "Post 1 text"}}
    {:id "post-2"
     :resource {:resourceType "Post" :id "post-2" :author {:id "account-3"} :text "Post 2 text"}}
    {:id "post-3"
     :resource {:resourceType "Post" :id "post-3" :author {:id "account-2"} :text "Post 3 text"}}
    {:id "post-4"
     :resource {:resourceType "Post" :id "post-4" :author {:id "account-1"} :text "Post 4 text"}}
    {:id "post-5"
     :resource {:resourceType "Post" :id "post-5" :author {:id "account-2"} :text "Post 5 text"}}])
  (jdbc/insert-multi!
   t-con
   :Commentary
   [{:id "commentary-1"
     :resource {:resourceType "Commentary" :id "commentary-1" :post {:id "post-4"} :text "Comment 1" :author {:id "account-2"}}}
    {:id "commentary-2"
     :resource {:resourceType "Commentary" :id "commentary-2" :post {:id "post-4"} :text "Comment 2" :author {:id "account-3"}}}
    {:id "commentary-3"
     :resource {:resourceType "Commentary" :id "commentary-3" :post {:id "post-2"} :text "Comment 3" :author {:id "account-1"}}}
    {:id "commentary-4"
     :resource {:resourceType "Commentary" :id "commentary-4" :post {:id "post-5"} :text "Comment 4" :author {:id "account-1"}}}
    {:id "commentary-5"
     :resource {:resourceType "Commentary" :id "commentary-5" :post {:id "post-4"} :text "Comment 5" :author {:id "account-1"}}}
    {:id "commentary-6"
     :resource {:resourceType "Commentary" :id "commentary-6" :post {:id "post-2"} :text "Comment 6" :author {:id "account-2"}}}])
  nil)

(defn prepare-db!
  [db-spec]
  (jdbc/with-db-transaction [t-con db-spec]
    (do
      (prepare-db!-create-tables t-con)
      (prepare-db!-truncate-tables t-con)
      (prepare-db!-insert-data t-con))))

(defn wrap-setup
  [f]
  (prepare-db! db-spec)
  (f))
