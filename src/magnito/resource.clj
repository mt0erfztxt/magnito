(ns magnito.resource
  (:require
   [clojure.string :as str]))

(defn add-defaults
  [resource parent-resource ref-key]
  (-> resource
      ;; Whether resource is root or not.
      (assoc :root? (not parent-resource))
      ;; :collection is optional and false by default.
      (#(if (contains? % :collection)
          %
          (assoc % :collection false)))
      ;; :elements is optional and :* by default.
      (#(if (contains? % :elements)
          %
          (assoc % :elements [:*])))
      ;; :reverse is optional and false by default.
      (#(if (contains? % :reverse)
          %
          (assoc % :reverse false)))
      ;; :id is optional and :id by default.
      (#(if (contains? % :id)
          %
          (assoc % :id :id)))
      ;; :by is nil for root resource, optional for non-root resource and
      ;; default to:
      ;; * [parent-resource-type-keyword (:id parent-resource)] when :reverse is true,
      ;; * [ref-key (:id resource)] when :reverse is false.
      (#(if (:root? %)
          (assoc % :by nil)
          (if (contains? % :by)
            %
            (if (:reverse %)
              (let [k (-> parent-resource (:resourceType) (str/lower-case) (keyword))]
                (assoc % :by [k (:id parent-resource)]))
              (assoc % :by [ref-key (:id %)])))))
      ;; :root? is true for root resource and false otherwise.
      (update :root? #(if (true? %) % false))))

(defn validate
  [{:keys [by collection elements id references reverse] :as resource}]
  (let [msg "Resource validation failed - "]
    (when-not (or (nil? by)
                  (and (vector? by) (every? keyword? by)))
      (throw
       (IllegalArgumentException.
        (str msg ":by must be a nil or a vector of keywords, "
             (dissoc resource :references)))))
    (when-not (contains? #{true false} collection)
      (throw
       (IllegalArgumentException.
        (str msg ":collection must be a boolean, "
             (dissoc resource :references)))))
    (when-not (and (vector? elements) (every? keyword? elements))
      (throw
       (IllegalArgumentException.
        (str msg ":elements must be a vector of keywords, "
             (dissoc resource :references)))))
    (when-not (keyword? id)
      (throw
       (IllegalArgumentException.
        (str msg ":id must be a keyword, "
             (dissoc resource :references)))))
    (when-not (or (map? references) (nil? references))
      (throw
       (IllegalArgumentException.
        (str msg ":references must be a map, "
             resource))))
    (when-not (contains? #{true false} reverse)
      (throw
       (IllegalArgumentException.
        (str msg ":reverse must be a boolean, "
             (dissoc resource :references)))))
    (when (and (true? collection) (false? reverse))
      (throw
       (IllegalArgumentException.
        (str msg ":reverse must be true when :collection is true, "
             (dissoc resource :references)))))
    resource))
