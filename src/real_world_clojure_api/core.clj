(ns real-world-clojure-api.core
  (:require [clojure.pprint :refer [pprint]]
            [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [next.jdbc.connection :as connection]
            [real-world-clojure-api.components.example-component
             :as example-component]
            [real-world-clojure-api.components.htmx-pedestal-component
             :as htmx-pedestal-component]
            [real-world-clojure-api.components.in-memory-state-component
             :as in-memory-state-component]
            [real-world-clojure-api.components.pedestal-component
             :as pedestal-component]
            [real-world-clojure-api.config :as config])
  (:import (com.zaxxer.hikari HikariDataSource)
           (org.flywaydb.core Flyway)))

(defn datasource-component
  [config]
  (connection/component
   HikariDataSource
   (assoc (:db-spec config)
          :init-fn (fn [data-source]
                     (log/info "Running database init")
                     (.migrate
                      (.. (Flyway/configure)
                          (dataSource data-source)
                          ; https://www.red-gate.com/blog/database-devops/flyway-naming-patterns-matter
                          (locations (into-array String ["classpath:database/migrations"]))
                          (table "schema_version")
                          (load)))))))

(defn real-world-clojure-api-system
  [config]
  (pprint "in real-world-clojure-api-system")
  (pprint "config")
  (pprint config)
  (config/assert-valid-config! config)
  (component/system-map
   :example-component
   (example-component/new-example-component config)
   ;;
   :in-memory-state-component
   (in-memory-state-component/new-in-memory-state-component config)
   ;;
   :data-source
   (datasource-component config)
   ;;
   :pedestal-component
   (component/using
    (pedestal-component/new-pedestal-component config)
    [:example-component
     :data-source
     :in-memory-state-component])
   ;;
   :htmx-pedestal-component
   (component/using
    (htmx-pedestal-component/new-htmx-pedestal-component config)
    [:in-memory-state-component])))

(defn -main
  []
  (let [system (-> (config/read-config)
                   (config/assert-valid-config!)
                   (real-world-clojure-api-system)
                   (component/start-system))]
    (println "Starting real world clojure api service with config")
    (.addShutdownHook
     (Runtime/getRuntime)
     (new Thread #(component/stop-system system)))))