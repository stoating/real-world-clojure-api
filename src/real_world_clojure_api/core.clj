(ns real-world-clojure-api.core
  (:require [real-world-clojure-api.config :as config]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [real-world-clojure-api.components.example-component :as example-component]
            [com.stuartsierra.component :as component]))

(defn respond-hello
  [request]
  {:status 200
   :body "Hello, world!"})

(def routes
  (route/expand-routes
   #{["/greet" :get respond-hello :route-name :greet]}))

(defn create-server
  [config]
  (http/create-server
   {::http/routes routes
    ::http/type   :jetty
    ::http/join   false
    ::http/port   (-> config :server :port)}))

(defn start
  [config]
  (http/start (create-server config)))

(defn real-world-clojure-api-system
  [config]
  (component/system-map
   :example-component (example-component/new-example-component config)))

(defn -main
  []
  (let [system (-> (config/read-config)
                   (real-world-clojure-api-system)
                   (component/start-system))]
    (println "Starting real world clojure api service with config")
    (.addShutdownHook (Runtime/getRuntime)
                      (new Thread #(component/stop-system system)))))
