(ns magnito.utils
  (:require
   [clojure.string :as str]
   [honeysql.core :as honeysql]))

(defn as-vector
  [any]
  (cond
    (vector? any) any
    (sequential? any) (vec any)
    :else [any]))

(defn cte-name->path
  [cte-str]
  {:pre [(or (string? cte-str) (nil? cte-str))]}
  (when cte-str
    (->> (str/split cte-str #"_")
         (mapv keyword))))

(defn cte-path->name
  [cte-vec & more]
  {:pre [(or (nil? cte-vec) (vector? cte-vec))
         (or (empty? more) (every? #(or (keyword? %) (nil? %)) more))]}
  (let [path
        (-> cte-vec
            (#(if % % []))
            (into more)
            (->> (remove nil?))
            (vec))]
    (->> path
         (reduce
          (fn [acc item]
            (-> item
                (name)
                (#(if acc (str acc "_" %) %))))
          nil)
         (#(if % (str/lower-case %) %)))))

(defn cte-path->ref-key
  [path]
  (let [rk (last path)]
    (when (keyword? rk)
      rk)))

(defn expand-cte-path
  [path segment]
  (when-not (and (nil? path) (nil? segment))
    (-> (if (empty? path) [:root] path)
        (vec)
        (conj segment)
        (#(remove nil? %))
        (vec))))

(defn shrink-cte-path
  [path]
  (let [p (-> path (vec) (drop-last))]
    (when (seq p)
      (vec p))))

(defn expand-acc-cte-path
  [acc segment]
  (-> acc
      (:cte-path)
      (expand-cte-path segment)
      (->> (assoc acc :cte-path))))

(defn shrink-acc-cte-path
  [acc]
  (->> acc
       (:cte-path)
       (shrink-cte-path)
       (assoc acc :cte-path)))

(defn to-cte-name
  [v & more]
  (let [cte-vec (as-vector v)]
    (apply cte-path->name cte-vec more)))

(defn to-cte-path
  [v]
  (cte-name->path v))

(defn to-json-path-segment
  [v]
  {:pre [(or (keyword? v) (string? v) (number? v))]}
  (let [v (if (keyword? v) (name v) v)]
    (-> v
        (#(str "'" % "'"))
        (keyword))))

(defn build-sql-json-path-extractor-call
  ([field resource-path] (build-sql-json-path-extractor-call field resource-path nil))
  ([field resource-path table-alias]
   {:pre [(or (keyword? field) (string? field))
          (or (nil? resource-path) (vector? resource-path))
          (or (nil? table-alias) (keyword? table-alias))]}
   (let [field (if table-alias (honeysql/qualify table-alias field) field)
         parts
         (->> resource-path
              (reduce
               (fn [acc item]
                 (if (nil? item)
                   acc
                   (conj acc (to-json-path-segment item))))
               [])
              (into [:jsonb_extract_path_text field]))]
     (apply honeysql/call parts))))
