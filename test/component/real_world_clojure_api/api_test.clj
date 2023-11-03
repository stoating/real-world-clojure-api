(ns component.real-world-clojure-api.api-test
  (:require [clj-http.client :as client]
            [com.stuartsierra.component :as component]
            [real-world-clojure-api.core :as core]
            [real-world-clojure-api.components.pedestal-component :refer [url-for]]
            [clojure.string :as str]
            [clojure.test :as t]
            [clojure.pprint :refer [pprint]]
            [cheshire.core :as json])
  (:import [java.net ServerSocket]))

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
  []
  {:server {:address localhost
            :port (get-free-port)}})

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

(t/deftest greeting-test
  (with-system
    [sut (core/real-world-clojure-api-system (m-config))]
    (new-test "greeting-test: return standard greeting")
    (let [url (sut->url sut (url-for :greet-get))
          response (http-get url)
          response-exp {:body "Hi Youtube" :status ok-code}
          response-act (select-keys response (keys response-exp))]
      (run-test response response-exp response-act))))

(t/deftest get-todo-test
  (let [todo-id (str (random-uuid))
        todo {:id todo-id
              :name "my test todo list"
              :items [{:id (str (random-uuid))
                       :name "finish the test"}]}]
    (with-system
      [sut (core/real-world-clojure-api-system (m-config))]
      (reset! (-> sut :in-memory-state-component :state-atom)
              [todo])
      (new-test "get-todo-test: body from state")
      (let [url (sut->url sut (url-for :todo-get {:path-params {:todo-id todo-id}}))
            response (http-get url :json :json)
            response-exp {:body todo :status ok-code}
            response-act (select-keys response (keys response-exp))]
        (run-test response response-exp response-act))
      (new-test "get-todo-test: empty body from todo not in state 404")
      (let [url (sut->url sut (url-for :todo-get {:path-params {:todo-id (random-uuid)}}))
            response (http-get url)
            response-exp {:body "" :status not-found-code}
            response-act (select-keys response (keys response-exp))]
        (run-test response response-exp response-act)))))

(t/deftest post-todo-test
  (let [todo-id (str (random-uuid))
        todo {:id todo-id
              :name "My todo for test"
              :items []}]
    (with-system
      [sut (core/real-world-clojure-api-system {:server {:port (get-free-port)}})]
      (new-test "post-todo-test: post todo to server")
      (let [url (sut->url sut (url-for :todo-post))
            body (json/encode todo)
            response (http-post url body :json :json :json)
            response-exp {:body todo :status created-code}
            response-act (select-keys response (keys response-exp))]
        (run-test response response-exp response-act))
      (new-test "post-todo-test: get after posting returns the todo")
      (let [url (sut->url sut (url-for :todo-get {:path-params {:todo-id todo-id}}))
            response (http-get url :json :json)
            response-exp {:body todo :status ok-code}
            response-act (select-keys response (keys response-exp))]
        (run-test response response-exp response-act))
      (new-test "post-todo-test: post with missing body content is 500")
      (let [url (sut->url sut (url-for :todo-post))
            body (json/encode {:id todo-id})
            response (http-post url body :json :json :json)
            response-exp {:status internal-server-error-code}
            response-act (select-keys response (keys response-exp))]
        (run-test response response-exp response-act)))))


(t/deftest content-negotiation-test
  (with-system
    [sut (core/real-world-clojure-api-system (m-config))]
    (new-test "content-negotiation-test: json shall be accepted")
    (let [url (sut->url sut (url-for :greet-get))
          response-exp {:body "Hi Youtube" :status ok-code}
          response (http-get url :json)
          response-act (select-keys response (keys response-exp))]
      (run-test response response-exp response-act))
    (new-test "content-negotiation-test: edn shall be rejected")
    (let [url (sut->url sut (url-for :greet-get))
          response (http-get url :edn)
          response-exp {:body not-acceptable-reason :status not-acceptable-code}
          response-act (select-keys response (keys response-exp))]
      (run-test response response-exp response-act))))