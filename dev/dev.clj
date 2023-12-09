(ns dev
  (:require [com.stuartsierra.component.repl :as component-repl]
            [real-world-clojure-api.core :as core]))

(component-repl/set-init
 (fn [_]
   (core/real-world-clojure-api-system
    {:server {:port 3001}
     :htmx {:server {:port 3002}}
     :db-spec {:jdbcUrl "jdbc:postgresql://localhost:5432/database"
               :username "username"
               :password "password"}
     :input-topics #{"a" "topic-1" "topic-2"}
     :kafka {:bootstrap.servers "kafka",
             :application.id "my-app",
             :auto.offset.reset "earliest",
             :producer.acks "all",
             :ssl.keystore.type "PKCS12",
             :ssl.truststore.type "JKS",
             :ssl.keystore.location "keystore-location",
             :ssl.keystore.password "keystore-password",
             :ssl.key.password "key-password",
             :ssl.truststore.location "truststore-location",
             :ssl.truststore.password "truststore-password",
             :security.protocol "SSL"}})))