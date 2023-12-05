(ns persistence.real-world-clojure-api.honeysql-test
  (:require [clojure.test :as t]
            [com.stuartsierra.component :as component]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [real-world-clojure-api.core :as core]
            [honey.sql :as sql])
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

(-> {:select :a
     :from :table-one
     :where [:= :a 1]}
    (sql/format)) ; ["SELECT a FROM table_one WHERE a = ?" 1]

(-> {:select :a
     :from [:table-one :table-two]
     :where [:= :a 1]}
    (sql/format)) ; ["SELECT a FROM table_one, table_two WHERE a = ?" 1]

(-> {:select [[:t1.a :t1a]:t2.*]
     :from [[:table-one :t1] [:table-two :t2]]
     :where [:and
             [:= :t1.id :t2.id]
             [:or
              [:= :t1.a 1]
              [:<> :t2.b "123"]]]}
    (sql/format)) ;["SELECT t1.a AS t1a, t2.* FROM table_one AS t1, table_two AS t2 WHERE (t1.id = t2.id) AND ((t1.a = ?) OR (t2.b <> ?))"
                  ; 1
                  ; "123"]



(-> {:select :a
     :from [[:table-one :t1] [:table-two :t2]]
     :where [:= :a 1]}
    (sql/format))

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
              select-query (sql/format {:select :*
                                        :from :schema-version})
              [schema-version :as schema-versions]
              (jdbc/execute!
               (data-source)
               select-query
               {:builder-fn rs/as-unqualified-lower-maps})]
          (t/is (= ["SELECT * FROM schema_version"]
                   select-query))
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
              insert-query (-> {:insert-into [:todo]
                                :columns [:title]
                                :values [["my todo list"]
                                         ["other todo list"]]
                                :returning :*}
                               (sql/format))
              insert-results (jdbc/execute!
                              (data-source)
                              insert-query
                              {:builder-fn rs/as-unqualified-lower-maps})
              select-results (jdbc/execute!
                              (data-source)
                              (-> {:select :*
                                   :from :todo}
                                  (sql/format))
                              {:builder-fn rs/as-unqualified-lower-maps})]
          (t/is (= ["INSERT INTO todo (title) VALUES (?), (?) RETURNING *"
                    "my todo list"
                    "other todo list"]
                   insert-query))
          (t/is (= 2 (count insert-results)
                     (count select-results)))
          (t/is (= #{"my todo list" "other todo list"}
                   (->> insert-results (map :title) (into #{}))
                   (->> select-results (map :title) (into #{}))))))
      (finally
        (.stop database-container)))))