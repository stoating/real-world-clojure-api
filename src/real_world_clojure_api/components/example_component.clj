(ns real-world-clojure-api.components.example-component
  (:require [com.stuartsierra.component :as component]))

(defrecord ExampleComponent [config]
  component/Lifecycle

  (start [component]
    (println "Starting ExampleComponent")
    (assoc component :state-atom (atom [])))

  (stop [component]
    (println "Stopping ExampleComponent")
    (assoc component :state-atom nil)))

(defn new-example-component
  [config]
  (map->ExampleComponent {:config config}))