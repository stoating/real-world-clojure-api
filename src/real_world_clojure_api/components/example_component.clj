(ns real-world-clojure-api.components.example-component
  (:require [com.stuartsierra.component :as component]))

(defrecord ExampleComponent [config]
  component/Lifecycle

  (start [component]
    (println ";; Starting ExampleComponentiii")
    (assoc component :state ::started))

  (stop [component]
    (println ";; Stopping ExampleComponentiii")
    (assoc component :state nil)))

(defn new-example-component
  [config]
  (map->ExampleComponent {:config config}))