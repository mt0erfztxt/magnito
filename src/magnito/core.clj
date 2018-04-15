(ns magnito.core
  (:require
   [clojure.string :as str]
   [honeysql.core :as honeysql]
   [honeysql.helpers :as honeysql-helpers]
   [magnito.resource :as resource]
   [magnito.utils :as utils]))

(defn- build-cte-part-sql-map-left-join-condition
  [cte-path {:keys [by] reverse? :reverse :as resource} {:keys [id] :as parent-resource}]
  (let [t1-qfield
        (->> cte-path
             (utils/shrink-cte-path)
             (utils/cte-path->ref-key)
             (honeysql/qualify :t1))
        t2-qfield :t2.resource
        t1-path (if reverse? [id] by)
        t2-path (if reverse? by [id])]
    [:=
     (apply utils/call-jsonb-extract-path-text-sql-func t1-qfield t1-path)
     (apply utils/call-jsonb-extract-path-text-sql-func t2-qfield t2-path)]))

(defn- build-cte-part-sql-map-left-join
  [m cte-path {:keys [root?] :as resource} parent-resource]
  (if root?
    m
    (honeysql-helpers/left-join
     m
     [(-> resource (:resourceType) (keyword)) :t2]
     (build-cte-part-sql-map-left-join-condition cte-path resource parent-resource))))

(defn- build-cte-part-sql-map-from
  [m cte-path {:keys [root?] :as resource}]
  (let [table
        (if root?
          (-> resource (:resourceType) (keyword))
          (-> cte-path (utils/shrink-cte-path) (utils/cte-path->name) (keyword)))]
    (honeysql-helpers/from m [table :t1])))

(defn- build-cte-part-sql-map-select
  [m cte-path {:keys [root?] :as resource}]
  (let [field-list
        (if root?
          [:t1.resource (-> resource (:resourceType) (str/lower-case) (keyword))]
          [:t2.resource (utils/cte-path->ref-key cte-path)])]
    (honeysql-helpers/select m field-list)))

(defn- build-cte-part-sql-map
  [{:keys [cte-path] :as acc} resource parent-resource]
  (let [cte-par-name (-> cte-path (utils/cte-path->name) (keyword))
        cte-part-body
        (-> {}
            (build-cte-part-sql-map-select cte-path resource)
            (build-cte-part-sql-map-from cte-path resource)
            (build-cte-part-sql-map-left-join cte-path resource parent-resource))]
    [cte-par-name cte-part-body]))

(defn- build-query-part-sql-map-left-join-condition
  [t1-qfield t2-qfield resource {:keys [by id] reverse? :reverse :as ref-resource}]
  (let [t1-path (if reverse? [(:id resource)] by)
        t2-path
        (if reverse?
          (if (:collection ref-resource) (into [0] by) by)
          [id])]
    [:=
     (apply utils/call-jsonb-extract-path-text-sql-func t1-qfield t1-path)
     (apply utils/call-jsonb-extract-path-text-sql-func t2-qfield t2-path)]))

;; We use resource and ref-resource args to extract values from json.
(defn- build-query-part-sql-map-left-join
  [m t2 t1-qfield t2-qfield resource ref-resource]
  (if ref-resource
    (let [left-join-condition
          (build-query-part-sql-map-left-join-condition
           t1-qfield
           t2-qfield
           resource
           ref-resource)]
      (honeysql-helpers/left-join m [t2 :t2] left-join-condition))
    m))

(defn- build-query-part-sql-map-from
  [m t1]
  (honeysql-helpers/from m [t1 :t1]))

(defn- build-query-part-sql-map-select
  [m t1-field t1-qfield t2-qfield joint-key]
  (let [fields-list
        (if joint-key   ; Non-root resource?
          [(honeysql/call
            :case [:= t2-qfield nil] t1-qfield
            :else (utils/call-jsonb-set-sql-func t1-qfield joint-key t2-qfield))
           t1-field]
          t1-qfield)]
    (honeysql-helpers/select m fields-list)))

(defn- build-query-part-sql-map
  [cte-path joint-key t1 resource t2 ref-resource]
  {:pre [(or (keyword? t1) (map? t1))
         (or (nil? t2) (keyword? t2) (map? t2))]}
  (let [t2
        (or t2
            (-> cte-path (utils/expand-cte-path joint-key) (utils/to-cte-name) (keyword)))
        t1-field (last cte-path)
        t2-field joint-key
        t1-qfield (honeysql/qualify :t1 t1-field)
        t2-qfield (honeysql/qualify :t2 t2-field)]
    (-> {}
        (build-query-part-sql-map-select t1-field t1-qfield t2-qfield joint-key)
        (build-query-part-sql-map-from t1)
        (build-query-part-sql-map-left-join t2 t1-qfield t2-qfield resource ref-resource))))

(defn- process-resource-build-cte-part
  [acc resource parent-resource]
  (->> (build-cte-part-sql-map acc resource parent-resource)
       (update acc :sql-ctes conj)))

(defn- process-resource-build-query-part-wrap-in-aggregate
  [query cte-path {:keys [by] :as resource}]
  {:pre [(or (keyword? query) (map? query))]}
  (let [t1-field (utils/cte-path->ref-key cte-path)
        t1-qfield (honeysql/qualify :t1 t1-field)
        groupping-value (apply utils/call-jsonb-extract-path-text-sql-func t1-qfield by)]
    (-> {}
        (honeysql-helpers/select
         [(utils/call-jsonb-agg-sql-func t1-qfield) t1-field])
        (honeysql-helpers/from [query :t1])
        (honeysql-helpers/group groupping-value)
        (honeysql-helpers/having [:is-not groupping-value nil]))))

;; NOTE Covered only by functional tests.
(defn- process-resource-build-query-part-wrap-in-distinct
  [query ref-key {:keys [id] :as resource}]
  (let [t1-qfield (honeysql/qualify :t1 ref-key)
        t1-qfield-for-order-by (utils/call-jsonb-extract-path-text-sql-func t1-qfield id)]
    (-> {}
        (honeysql-helpers/select [t1-qfield ref-key] t1-qfield-for-order-by)
        (honeysql-helpers/modifiers :distinct)
        (honeysql-helpers/from [query :t1])
        (honeysql-helpers/order-by t1-qfield-for-order-by))))

(defn- process-resource-build-query-part
  [{:keys [cte-path ref-queries] :as acc} {:keys [references] :as resource} parent-resource]
  (let [cte-key (-> cte-path (utils/cte-path->name) (keyword))
        ref-key (utils/cte-path->ref-key cte-path)
        result
        (if (empty? references)
          (if (:root? resource)
            (build-query-part-sql-map cte-path nil cte-key resource nil nil)
            cte-key)
          (reduce-kv
           (fn [t1 k v]
             (let [ref-resource (resource/add-defaults v resource k)
                   t2 (get ref-queries k)]
               (build-query-part-sql-map cte-path k t1 resource t2 ref-resource)))
           cte-key
           (:references resource)))]
    (->> (#(if (:collection resource)
             (-> result
                 (process-resource-build-query-part-wrap-in-distinct ref-key resource)
                 (process-resource-build-query-part-wrap-in-aggregate cte-path resource))
             result))
         (assoc {} ref-key)
         (assoc acc :ref-queries))))

(defn- process-resource
  [acc ref-key resource parent-resource]
  {:pre [(or (and (nil? ref-key) (nil? parent-resource))
             (and (keyword? ref-key) (map? parent-resource)))]}
  (let [ref-key
        (or ref-key
            (-> resource
                (:resourceType)
                (name)
                (str/lower-case)
                (keyword)))
        resource
        (-> resource
            (resource/add-defaults parent-resource ref-key)
            (resource/validate))]
    (-> acc
        (utils/expand-acc-cte-path ref-key)
        (process-resource-build-cte-part resource parent-resource)
        ((fn [acc]
           (reduce-kv
            (fn [m k v]
              (-> m
                  (process-resource k v resource)
                  (utils/shrink-acc-cte-path)))
            acc
            (:references resource))))
        (process-resource-build-query-part resource parent-resource)
        (#(if (:root? resource)
            (assoc % :sql-query (get-in % [:ref-queries ref-key]) :ref-queries nil)
            %)))))

(defn- process
  [resource]
  (let [acc
        {:cte-path []
         :ref-queries nil
         :sql-ctes []
         :sql-query nil}]
    (process-resource acc nil resource nil)))

(defn- resource->sql-aggregate-results
  [{:keys [cte-path sql-query] :as acc} resource {:keys [separate?]}]
  (if separate?
    acc
    (let [t1-field (utils/cte-path->ref-key cte-path)
          t1-qfield (honeysql/qualify :t1 t1-field)]
      (-> {}
          (honeysql-helpers/select [(utils/call-jsonb-agg-sql-func t1-qfield) :result])
          (honeysql-helpers/from [sql-query :t1])
          (->> (assoc acc :sql-query))))))

(defn resource->sql
  ([resource] (resource->sql resource nil))
  ([resource {:keys [str?] :as options}]
   (let [{:keys [sql-ctes sql-query] :as acc}
         (-> resource
             (process)
             (resource->sql-aggregate-results resource options))
         sql-map (apply honeysql-helpers/with sql-query sql-ctes)
         [x & xs :as sql-vec] (honeysql.format/format sql-map)]
     (if str?
       (-> x
           (str/replace "?" "%s")
           (#(apply format % xs)))
       sql-vec))))

;; (println "\n\n-------------------------------------------------------------------------------")
;; (clojure.pprint/pprint
;;  (resource->sql
;;   {:resourceType "Account"
;;    :references
;;    {
;;     :profile {:resourceType "Profile"}
;;     :posts
;;     {:resourceType "Post"
;;      :by [:author :id]
;;      :collection true
;;      :reverse true
;;      :references
;;      {:commentaries
;;       {:resourceType "Commentary"
;;        :by [:post :id]
;;        :collection true
;;        :reverse true
;;        :references {:author {:resourceType "Account"}}
;;        }
;;       }
;;      }
;;     }
;;    }
;;   {:separate? false
;;    :str? true}))
