(ns persistence.real-world-clojure-api.migrations-test
  (:require [clojure.test :as t]
            [com.stuartsierra.component :as component]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [real-world-clojure-api.core :as core])
  (:import (org.testcontainers.containers PostgreSQLContainer)))

;; declare var which will be introduced after executing 'with-system' macro
(declare sut)

(defmacro with-system
  [[bound-var binding-expr] & body]
  `(let [~bound-var (component/start ~binding-expr)]
     (try
       ~@body
       (finally
         (component/stop ~bound-var)))))

(defn datasource-only-system
  [config]
  (component/system-map
   :data-source (core/datasource-component config)))

(defn create-database-container
  []
  (PostgreSQLContainer. "postgres:15.4"))

;; not a test to run every time, but useful to have
(t/deftest migrations-test
  (let [database-container (create-database-container)]
    (try
      (.start database-container)
      (with-system
        [sut (datasource-only-system
              {:db-spec {:jdbcUrl (.getJdbcUrl database-container)
                         :username (.getUsername database-container)
                         :password (.getPassword database-container)}})]
        (let [{:keys [data-source]} sut
              [schema-version :as schema-versions]
              (jdbc/execute!
               (data-source)
               ["select * from schema_version"]
               {:builder-fn rs/as-unqualified-lower-maps})]
          (t/is (= 1 (count schema-versions)))
          (t/is (= {:description "add todo tables"
                    :script "V1__add_todo_tables.sql"
                    :success true}
                   (select-keys schema-version [:description :script :success])))))
      (finally
        (.stop database-container)))))

(t/deftest todo-table-test
  (let [database-container (create-database-container)]
    (try
      (.start database-container)
      (with-system
        [sut (datasource-only-system
              {:db-spec {:jdbcUrl (.getJdbcUrl database-container)
                         :username (.getUsername database-container)
                         :password (.getPassword database-container)}})]
        (let [{:keys [data-source]} sut
              insert-results (jdbc/execute!
                              (data-source)
                              ["insert into todo (title)
                                values ('my todo list'),
                                ('other todo list')
                                returning *"]
                              {:builder-fn rs/as-unqualified-lower-maps})
              select-results (jdbc/execute!
                              (data-source)
                              ["select * from todo"]
                              {:builder-fn rs/as-unqualified-lower-maps})]
          (t/is (= 2 (count insert-results)
                     (count select-results)))
          (t/is (= #{"my todo list" "other todo list"}
                   (->> insert-results (map :title) (into #{}))
                   (->> select-results (map :title) (into #{}))))))
      (finally
        (.stop database-container)))))