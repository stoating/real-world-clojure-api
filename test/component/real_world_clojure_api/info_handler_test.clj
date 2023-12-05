(ns component.real-world-clojure-api.info-handler-test
  (:require [clj-http.client :as client]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as str]
            [clojure.test :as t]
            [com.stuartsierra.component :as component]
            [real-world-clojure-api.components.pedestal-component :refer [url-for]]
            [real-world-clojure-api.core :as core])
  (:import [java.net ServerSocket]
           [org.testcontainers.containers PostgreSQLContainer]))

(defn long-str [& strings] (clojure.string/join "\n" strings))
(defn new-test
  [s]
  (print (long-str
          ""
          "*******************************"
          s
          "*******************************"
          "")))
(defn print-exp-act
  [exp act]
  (println "exp:" (pr-str exp))
  (println "act:" (pr-str act))
  (println ""))

;; declare var which will be introduced after executing 'with-system' macro
(declare sut)

;; high-level defines
(def localhost "http://localhost:")
(def status {:ok {:code 200
                  :reason "OK"}
             :created {:code 201
                       :reason "Created"}
             :not-found {:code 404
                         :reason "Not Found"}
             :not-acceptable {:code 406
                              :reason "Not Acceptable"}
             :internal-server-error {:code 500
                                     :reason "Internal Server Error"}})

(def ok-code (get-in status [:ok :code]))

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
    :htmx {:server {:port (get-free-port)}}
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

(defn http-get
  ([url]
   (-> url
       (client/get {:throw-exceptions false})))
  ([url accept-type]
   (-> url
       (client/get {:accept accept-type
                    :throw-exceptions false})))
  ([url accept-type as-type]
   (-> url
       (client/get {:accept accept-type
                    :as as-type
                    :throw-exceptions false}))))

(defn sut->url
  [sut path]
  (str/join [m-address
             (-> sut :pedestal-component :config :server :port)
             path]))

(defn run-test
  [response response-exp response-act]
  (pprint response)
  (print-exp-act response-exp response-act)
  (t/is (= response-exp
           response-act)))

(t/deftest info-handler-test
  (let [database-container (doto (PostgreSQLContainer. "postgres:15.4")
                             (.withDatabaseName "test-database-name")
                             (.withUsername "Zachary")
                             (.withPassword "test-password"))]
    (try
      (.start database-container)
      (with-system
        [sut (core/real-world-clojure-api-system (m-config database-container))]
        (new-test "info-handler-test: return standard greeting")
        (let [url (sut->url sut (url-for :info-get))
              response (http-get url)
              response-exp {:body "Database server version 15.4 (Debian 15.4-2.pgdg120+1)",
                            :status ok-code}
              response-act (select-keys response (keys response-exp))]
          (run-test response response-exp response-act)))
      (finally
        (.stop database-container)))))