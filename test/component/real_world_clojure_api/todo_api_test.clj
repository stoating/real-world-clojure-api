(ns component.real-world-clojure-api.todo-api-test
  (:require [clj-http.client :as client]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as str]
            [clojure.test :as t]
            [com.stuartsierra.component :as component]
            [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [real-world-clojure-api.components.pedestal-component :refer [url-for]]
            [real-world-clojure-api.core :as core]
            [next.jdbc.result-set :as rs])
  (:import [java.net ServerSocket]
           [org.testcontainers.containers PostgreSQLContainer]))

;; declare var which will be introduced after executing 'with-system' macro
(declare sut)

;; high-level defines
(def localhost "http://localhost:")

(defn get-free-port
  []
  (with-open [socket (ServerSocket. 0)]
    (.getLocalPort socket)))

(defn m-config
  ([]
   {:server {:address localhost
             :port (get-free-port)}})
  ([database-container]
   {:server {:address localhost
             :port (get-free-port)}
    :htmx   {:server {:port (get-free-port)}}
    :db-spec {:jdbcUrl (.getJdbcUrl database-container)
              :userName (.getUsername database-container)
              :password (.getPassword database-container)}}))

;; low-level defines
(def m-address (get-in (m-config) [:server :address]))


(defmacro with-system
  [[bound-var binding-expr] & body]
  `(let [~bound-var (component/start ~binding-expr)]
     (try
       ~@body
       (finally
         (component/stop ~bound-var)))))

(defn sut->url
  [sut path]
  (str/join [m-address
             (-> sut :pedestal-component :config :server :port)
             path]))

(t/deftest get-todo-test
  (let [database-container (doto (PostgreSQLContainer. "postgres:15.4")
                             (.withDatabaseName "test-database-name")
                             (.withUsername "Zachary")
                             (.withPassword "test-password"))]
    (try
      (println "starting database-container")
      (.start database-container)
      (println "database-container" database-container)
      (with-system
        [sut (core/real-world-clojure-api-system (m-config database-container))]
        (let [{:keys [data-source]} sut
              {:keys [todo-id
                      title]} (jdbc/execute-one!
                               (data-source)
                               (-> {:insert-into [:todo]
                                    :columns [:title]
                                    :values [["my test todo list"]]
                                    :returning :*}
                                   (sql/format))
                               {:builder-fn rs/as-unqualified-kebab-maps})
              {:keys [status
                      body]} (-> (sut->url sut
                                           (url-for :db-todo-get
                                                    {:path-params {:todo-id todo-id}}))
                                 (client/get {:accept :json
                                              :as :json
                                              :throw-exceptions false})
                                 (select-keys [:body :status]))]
          ;
          ;
          (println "printing body")
          (pprint body)
          (println "printing status")
          (pprint status)
          ;
          ;
          (t/is (= status 200))
          (t/is (some? (:created-at body)))
          (t/is (=  {:todo-id (str todo-id)
                     :title title}
                    (select-keys body [:todo-id :title]))))
        ;
        ;
        (t/testing "Empty body is return for random todo_id"
          (t/is (= {:body ""
                    :status 404}
                   (-> (sut->url sut
                                 (url-for :db-todo-get
                                          {:path-params {:todo-id (random-uuid)}}))
                       (client/get {:accept :json
                                    :as :json
                                    :throw-exceptions false})
                       (select-keys [:body :status]))))))
      (finally
        (println "stopping database-container")
        (.stop database-container)))))