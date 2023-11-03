(ns dev
  (:require [com.stuartsierra.component.repl :as component-repl]
            [real-world-clojure-api.core :as core]))

(component-repl/set-init
 (fn [_]
   (core/real-world-clojure-api-system
    {:server {:port 3001}
     :db-spec {:jdbcUrl "jdbc:postgresql://localhost:5432/database"
               :username "username"
               :password "password"}})))