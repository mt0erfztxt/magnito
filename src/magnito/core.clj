(ns magnito.core
  (:require
   [honeysql.core :as honeysql]
   [honeysql.helpers :as honeysql-helpers]
   [magnito.resource :as resource]
   [magnito.utils :as utils]
   [honeysql.core :as honeysql]
   [clojure.string :as str]))

(defn- build-left-join-condition
  [cte-path {:keys [by collection? id joint reverse?] :or {joint :resource}}]
  (let [parent-ref-key
        (-> cte-path
            (utils/shrink-cte-path)
            (utils/cte-path->ref-key))
        t1-part
        (if reverse?
          [parent-ref-key [id] :t1]
          [parent-ref-key by :t1])
        t2-part
        (if reverse?
          ;; [joint by :t2]
          (if collection? [joint (into [0] by) :t2] [joint by :t2])
          [joint [id] :t2])]
    [:=
     (apply utils/build-sql-json-path-extractor-call t1-part)
     (apply utils/build-sql-json-path-extractor-call t2-part)]))

(defn- process-resource-build-cte-part
  [{:keys [cte-path] :as acc} {:keys [by id root?] reverse? :reverse :as resource}]
  (let [ref-key (utils/cte-path->ref-key cte-path)
        cte-body
        (if root?
          (-> {}
              (honeysql-helpers/select [:t1.resource ref-key])
              (honeysql-helpers/from [(-> resource (:resourceType) (keyword)) :t1]))
          (let [parent-cte-path (utils/shrink-cte-path cte-path)
                parent-cte-name (utils/to-cte-name parent-cte-path)]
            (-> {}
                (honeysql-helpers/select [:t2.resource ref-key])
                (honeysql-helpers/from [(keyword parent-cte-name) :t1])
                (honeysql-helpers/left-join
                 [(keyword (:resourceType resource)) :t2]
                 (build-left-join-condition
                  cte-path
                  {:by by
                   :id id
                   :reverse? reverse?})))))]
    (update acc :sql-ctes conj [(-> cte-path (utils/to-cte-name) (keyword)) cte-body])))

(defn- build-query-part-add-from
  [m cte-path]
  (honeysql-helpers/from m [(keyword (utils/to-cte-name cte-path)) :t1]))

(defn- build-query-part-add-left-join
  [m cte-path sql-query joint-key {:keys [by id] :as ref-resource}]
  (let [{collection? :collection reverse? :reverse} ref-resource
        child-cte-path (utils/expand-cte-path cte-path joint-key)
        t2 (or sql-query
               (-> child-cte-path (utils/to-cte-name) (keyword)))]
    (honeysql-helpers/left-join
     m
     [t2 :t2]
     (build-left-join-condition
      child-cte-path
      {:by by
       :collection? collection?
       :id id
       :joint joint-key
       :reverse? reverse?}))))

(defn- build-query-part-add-select
  [m cte-path joint-key {:keys [collection] :as ref-resource}]
  (let [json-joint
        (-> joint-key
            (name)
            (#(str "{" % "}"))
            (utils/to-json-path-segment))
        new-joint-key (last cte-path)
        trg (honeysql/qualify :t1 new-joint-key)
        src (honeysql/qualify :t2 joint-key)]
    (honeysql-helpers/select
     m
     [(honeysql/call
       :case
       [:= (honeysql/qualify :t2 joint-key) nil] trg
       :else (honeysql/call :jsonb_set trg json-joint src))
      new-joint-key])))

(defn- build-query-part-aggregate-collection-resource
  [cte-path-or-sql-query joint-key {:keys [by] :as ref-resource}]
  (let [qualified-joint-key (honeysql/qualify :t1 joint-key)
        json-field (utils/build-sql-json-path-extractor-call qualified-joint-key by)
        t1
        (if (map? cte-path-or-sql-query)
          cte-path-or-sql-query
          (-> cte-path-or-sql-query
              (utils/expand-cte-path joint-key)
              (utils/to-cte-name)
              (keyword)))]
    (-> {}
        (honeysql-helpers/select
         [(honeysql/call :jsonb_agg qualified-joint-key) joint-key])
        (honeysql-helpers/from [t1 :t1])
        (honeysql-helpers/group json-field)
        (honeysql-helpers/having [:is-not json-field nil]))))

(defn- build-query-part
  [{:keys [cte-path sql-query] :as acc} resource]
  ;; (println "build-query-part()" cte-path)
  (->> resource
       (:references)
       (reduce
        (fn [sql-query-acc [k v]]
          (let [ref-cte-path (utils/expand-cte-path cte-path k)
                ref-resource (resource/add-defaults v resource ref-cte-path)
                ref-collection? (:collection ref-resource)
                ;; In case of collection resource we must aggregate in separate
                ;; step or otherwise resulting JSON would have arrays of nulls
                ;; for parent resources that have no collection item(-s).
                sql-query-acc
                (if ref-collection?
                  (build-query-part-aggregate-collection-resource
                   (or sql-query-acc cte-path)   ; we can already have an SQL query built in previous step
                   k
                   ref-resource)
                  sql-query-acc)]
            (-> {}
                (build-query-part-add-select cte-path k ref-resource)
                (build-query-part-add-from cte-path)
                (build-query-part-add-left-join
                 cte-path
                 (and ref-collection? sql-query-acc)
                 k
                 ref-resource))))
        sql-query)
       (#(if (:root? resource)
           (-> {}
               (honeysql-helpers/select [(honeysql/call :jsonb_agg :t1.root) :result])
               (honeysql-helpers/from [% :t1]))
           %))
       (assoc acc :sql-query)))

(defn- process-resource
  [{:keys [cte-path parent-resource] :as acc} {:keys [root?] :as resource}]
  (let [ref-key (utils/cte-path->ref-key cte-path)
        resource
        (-> resource
            (resource/add-defaults parent-resource cte-path)
            (resource/validate))]
    (-> acc
        (process-resource-build-cte-part resource)
        ((fn [acc]
           (reduce-kv
            (fn [m k v]
              (-> m
                  (utils/expand-acc-cte-path k)
                  (process-resource v)
                  (utils/shrink-acc-cte-path)))
            acc
            (:references resource))))
        (build-query-part resource))))

(defn- process
  [resource]
  (let [acc
        {:cte-path [:root]
         :sql-ctes []
         :sql-query nil}]
    (->> (assoc resource :root? true)
         (process-resource acc))))

(defn resource->sql
  ([resource] (resource->sql resource nil))
  ([resource {:keys [str?]}]
   (let [{:keys [sql-ctes sql-query]} (process resource)
         sql-map (apply honeysql-helpers/with sql-query sql-ctes)
         [x & xs :as sql-vec] (honeysql.format/format sql-map)]
     (if str?
       (-> x
           (str/replace "?" "%s")
           (#(apply format % xs)))
       sql-vec))))

;; (println "\n\n-------------------------------------------------------------------------------")
;; (let [{:keys [sql-ctes sql-query] :as acc}
;;       (process
;;        {:resourceType "Account"
;;         :references
;;         {
;;          ;; :profile {:resourceType "Profile"}
;;          :posts
;;          {:resourceType "Post"
;;           :by [:author :id]
;;           :collection true
;;           :reverse true
;;           :references
;;           {:commentaries
;;            {:resourceType "Commentary"
;;             :by [:post :id]
;;             :collection true
;;             :reverse true
;;             :references {:author {:resourceType "Account"}}
;;             }
;;            }
;;           }
;;          }
;;         })]
;;   ;; (println "\n+++++++++\nacc")
;;   ;; (clojure.pprint/pprint acc)
;;   ;; (clojure.pprint/pprint sql-ctes)
;;   (let [res (apply honeysql-helpers/with sql-query sql-ctes)]
;;     ;; (clojure.pprint/pprint (honysql.format/format res))
;;     (let [[x & xs] (honeysql.format/format res)]
;;       (-> x
;;           (str/replace "?" "%s")
;;           (#(apply format % xs))
;;           (clojure.pprint/pprint)))
;;     )
;;   )

;; (println "\n\n-------------------------------------------------------------------------------")
;; (clojure.pprint/pprint
;;  (resource->sql
;;   {:resourceType "Account"
;;    :references
;;    {
;;     :profile {:resourceType "Profile"}
;;     ;; :posts
;;     ;; {:resourceType "Post"
;;     ;;  :by [:author :id]
;;     ;;  :collection true
;;     ;;  :reverse true
;;     ;;  :references
;;     ;;  {:commentaries
;;     ;;   {:resourceType "Commentary"
;;     ;;    :by [:post :id]
;;     ;;    :collection true
;;     ;;    :reverse true
;;     ;;    :references {:author {:resourceType "Account"}}
;;     ;;    }
;;     ;;   }
;;     ;;  }
;;     }
;;    }
;;   #_{:str? true}))
