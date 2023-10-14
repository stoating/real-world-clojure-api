(ns real-world-clojure-api.core
  (:require [com.stuartsierra.component :as component]
            [real-world-clojure-api.config :as config]
            [real-world-clojure-api.components.example-component
             :as example-component]
            [real-world-clojure-api.components.in-memory-state-component
             :as in-memory-state-component]
            [real-world-clojure-api.components.pedestal-component
             :as pedestal-component]
            [next.jdbc.connection :as connection])
  (:import (com.zaxxer.hikari HikariDataSource)))

(defn real-world-clojure-api-system
  [config]
  (component/system-map
   :example-component
   (example-component/new-example-component config)
   ;;
   :in-memory-state-component
   (in-memory-state-component/new-in-memory-state-component config)
   ;;
   :data-source
   (connection/component HikariDataSource (:db-spec config))
   ;;
   :pedestal-component
   (component/using
    (pedestal-component/new-pedestal-component config)
    [:example-component
     :in-memory-state-component])))

(defn -main
  []
  (let [system (-> (config/read-config)
                   (real-world-clojure-api-system)
                   (component/start-system))]
    (println "Starting real world clojure api service with config")
    (.addShutdownHook
     (Runtime/getRuntime)
     (new Thread #(component/stop-system system)))))