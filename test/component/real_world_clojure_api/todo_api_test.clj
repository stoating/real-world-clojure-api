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
(def created-code (get-in status [:created :code]))
(def not-found-code (get-in status [:not-found :code]))
(def not-acceptable-code (get-in status [:not-acceptable :code]))
(def not-acceptable-reason (get-in status [:not-acceptable :reason]))
(def internal-server-error-code (get-in status [:internal-server-error :code]))

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

(defn http-post
  [url body accept content-type as]
  (-> url
      (client/post {:accept accept
                    :content-type content-type
                    :as as
                    :throw-exceptions false
                    :body body})))

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
          (t/is (= status 200))
          (t/is (some? (:created-at body)))
          (t/is (=  {:todo-id (str todo-id)
                     :title title}
                    (select-keys body [:todo-id :title])))))
        ;
        ;
        #_ (new-test "get-todo-test: body from state")
        #_ (let [url (sut->url sut (url-for :db-todo-get {:path-params {:todo-id todo-id}}))
                 response (http-get url :json :json)
                 response-exp {:body todo :status ok-code}
                 response-act (select-keys response (keys response-exp))]
             (run-test response response-exp response-act))
        ;
        ;
        #_ (new-test "get-todo-test: empty body from todo in state 404")
        #_ (let [url (sut->url sut (url-for :db-todo-get {:path-params {:todo-id (random-uuid)}}))
                 response (http-get url)
                 response-exp {:body "" :status not-found-code}
                 response-act (select-keys response (keys response-exp))]
             (run-test response response-exp response-act))
        (finally
          (println "stopping database-container")
          (.stop database-container)))))