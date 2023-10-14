(ns persistence.real-world-clojure-api.simple-test
  (:require [clojure.test :as t]
            [next.jdbc :as jdbc])
  (:import (org.testcontainers.containers PostgreSQLContainer)))

(t/deftest a-simple-persistence-test
  (let [database-container (doto (PostgreSQLContainer. "postgres:15.4")
                             (.withDatabaseName "some-database-name")
                             (.withUsername "Zachary")
                             (.withPassword "some-password"))]
    (try
      (.start database-container)
      (let [ds (jdbc/get-datasource {:jdbcUrl (.getJdbcUrl database-container)
                                     :userName (.getUsername database-container)
                                     :password (.getPassword database-container)})]
        (t/is (= {:result 1}
                 (first (jdbc/execute! ds ["select 1 as result;"])))))
      (finally
        (.stop database-container)))))
