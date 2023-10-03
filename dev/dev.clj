(ns dev
  (:require [com.stuartsierra.component.repl :as component-repl]
            [real-world-clojure-api.core :as mycore]))

(component-repl/set-init
 (fn [_old-system]
   (mycore/real-world-clojure-api-system {:server {:port 3001}})))