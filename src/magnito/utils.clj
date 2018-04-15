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
         (#(when %
             (->> % (str/lower-case) (str"_")))))))

(defn cte-path->ref-key
  [path]
  (let [rk (last path)]
    (when (keyword? rk)
      rk)))

(defn expand-cte-path
  [path segment]
  (when-not (and (nil? path) (nil? segment))
    (-> (if (empty? path) [] path)
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

(defn to-sql-json-attr
  [v]
  (when-not (or (keyword? v)
                (and (string? v) (re-matches #"\S+" v))
                (number? v))
    (throw
     (IllegalArgumentException. "argument must be a keyword, non-blank string or number")))
  (let [v (if (keyword? v) (name v) v)]
    (-> v
        (#(str "'" % "'"))
        (keyword))))

(defn call-jsonb-agg-sql-func
  [field]
  (honeysql/call :jsonb_agg field))

(defn call-jsonb-extract-path-text-sql-func
  "* `:field` - keyword or string.
   * `:path-segments` - keyword or string. `nil`s ignored."
  ([field & path-segments]
   {:pre [(or (keyword? field) (string? field))]}
   (let [field (if (keyword? field) field (keyword field))
         parts
         (->> path-segments
              (reduce
               (fn [acc item]
                 (if (nil? item)
                   acc
                   (conj acc (to-sql-json-attr item))))
               [])
              (into [:jsonb_extract_path_text field]))]
     (apply honeysql/call parts))))

(defn call-jsonb-set-sql-func
  "* `:path` - keyword or seq of keywords."
  [t1-field path t2-field]
  (let [path-str
        (->> path
             (as-vector)
             (map name)
             (str/join ",")
             (#(str "'{" % "}'"))
             (keyword))]
    (honeysql/call :jsonb_set t1-field path-str t2-field)))

(defn unqualify-field
  [v]
  {:pre [(or (keyword? v) (string? v))]}
  (let [[table-alias field] (-> v (name) (str/split #"\."))]
    (-> field
        (or table-alias)
        (keyword))))
