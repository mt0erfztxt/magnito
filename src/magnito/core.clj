(ns magnito.core
  (:require
   [clojure.string :as str]
   [honeysql.format :as honeysql-format]))

(defmethod honeysql-format/fn-handler "json-op"
  [_ op]
  (let [op (if (vector? op) op [op])]
    (reduce
     (fn [acc item]
       (let [v
             (cond
               (keyword? item) (name item)
               (string? item) (str "'" item "'")
               :else item)]
         (str acc v)))
     ""
     op)))

(def q1
  {:where [:= [:json-op [:resource :-> "profile" :->> "id"]] 18]})

(honeysql-format/format q1)

(def q2
  {:where [:= [:json-op :#>>] 18]})

(honeysql-format/format q2)

(def query
  {:resourceType "User"
   :references
   {:profile
    {:resourceType "Profile"
     :elements [:name]}
    :posts
    {:resourceType "Post"
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

(def sql-map
  {:with
   [[:user {:select [:*] :from [:User]}]
    [:user-ref-profile
     {:select [#sql/call [:jsonb-extract-path-text "'profile'" "'id'"]]
      :from [:Profile]}]]})

(honeysql-format/format sql-map)

(declare build-sql-cte)
(declare build-sql-add-references-sql)

(defn- build-sql-add-resource-sql
  [resource-map sql-map cte-key paths]
  (println cte-key)
  (let [paths (if (= "" cte-key) paths (assoc paths cte-key {}))
        rel-key
        (-> resource-map
            (:resourceType)
            (keyword))
        cte-key
        (->> rel-key
             (name)
             (#(if (= "" cte-key) % (str (name cte-key) "_" %)))
             (str/lower-case)
             (keyword))
        sql-map (update sql-map :with conj [cte-key {:select [:*] :from [rel-key]}])]
    (println "paths" paths)
    (if-let [references (:references resource-map)]
      (build-sql-add-references-sql references sql-map rel-key paths)
      sql-map)))

(defn- build-sql-add-references-sql-forward
  [reference-key resource-map sql-map cte-key paths]
  (println "build-sql-add-references-sql-forward" reference-key resource-map cte-key paths)
  (build-sql-cte resource-map sql-map cte-key paths))

(defn- build-sql-add-references-sql-reverse
  [reference-key resource-map sql-map cte-key paths]
  (build-sql-cte resource-map sql-map cte-key paths))

(defn- build-sql-add-references-sql
  [references-map sql-map cte-key paths]
  (reduce-kv
   (fn [m k v]
     (let [reverse? (:reverse v)]
       (if reverse?
         (build-sql-add-references-sql-reverse k v sql-map cte-key paths)
         (build-sql-add-references-sql-forward k v sql-map cte-key paths))))
   sql-map
   references-map))

(defn- build-sql-cte
  ([resource-map]
   (build-sql-cte resource-map {:with []}))
  ([resource-map sql-map]
   (build-sql-cte resource-map sql-map ""))
  ([resource-map sql-map cte-key]
   (build-sql-cte resource-map sql-map "" {}))
  ([resource-map sql-map cte-key paths]
   (loop [resource-map resource-map
          sql-map (build-sql-add-resource-sql resource-map sql-map cte-key paths)]
     (if (-> resource-map (:references) (nil?))
       {:sql-map sql-map :paths paths}
       (recur (:references resource-map) sql-map)))))

(defn build-sql
  [resource-map]
  (let [{:keys [paths sql-map]} (build-sql-cte resource-map)]
    (println "built paths")
    (clojure.pprint/pprint paths)
    sql-map))

(println "\n\n")
(clojure.pprint/pprint
 (build-sql
  {:resourceType "Account"
   :references
   {:profile
    {:resourceType "Profile"}
    :posts
    {:resourceType "Post"
     :reverse true
     :collection true
     :by [:author :id]}}}))
