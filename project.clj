(defproject magnito "0.1.0-SNAPSHOT"
  :description "A small DSL that generates SQL query to eagerly load enitites, with any level of nesting, from DB."
  :url "https://github.com/mt0erfztxt/magnito"
  :license
  {:name "Eclipse Public License"
   :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies
  [[honeysql "0.9.2"]
   [org.clojure/clojure "1.9.0"]]
  :profiles
  {:dev
   {:dependencies
    [[cheshire "5.8.0"]
     [org.clojure/java.jdbc "0.7.5"]
     [org.postgresql/postgresql	"42.2.2.jre7"]]}})
