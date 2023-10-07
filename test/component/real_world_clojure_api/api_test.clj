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
                              :reason "Not Acceptable"}})

(def ok-code (get-in status [:ok :code]))
(def created-code (get-in status [:created :code]))
(def not-found-code (get-in status [:not-found :code]))
(def not-acceptable-code (get-in status [:not-acceptable :code]))
(def not-acceptable-reason (get-in status [:not-acceptable :reason]))

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

(defn get-response
  ([url]
   (-> url
       (client/get {:throw-exceptions false})))
  ([url keys]
   (-> url
       (client/get {:throw-exceptions false})
       (select-keys keys)))
  ([url keys content-type]
   (-> url
       (client/get {:accept content-type
                    :throw-exceptions false})
       (select-keys keys)))
  ([url keys content-type as-type]
   (-> url
       (client/get {:accept content-type
                    :as as-type
                    :throw-exceptions false})
       (select-keys keys))))

(defn sut->url
  [sut path]
  (str/join [m-address
             (-> sut :pedestal-component :config :server :port)
             path]))

(t/deftest greeting-test
  (with-system
    [sut (core/real-world-clojure-api-system (m-config))]
    (new-test "greeting-test: return standard greeting")
    (let [url (sut->url sut (url-for :greet))
          response-exp {:body "Hi Youtube"
                        :status ok-code}
          response-act (get-response url (keys response-exp))]
      (pprint (get-response url [:headers]))
      (print-exp-act response-exp response-act)
      (t/is (= response-exp
               response-act)))))

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
      ;;
      (new-test "get-todo-test: body from state")
      (let [url (sut->url sut (url-for :get-todo {:path-params {:todo-id todo-id}}))
            content-type :json
            as-type :json
            response-exp {:body todo
                          :status ok-code}
            response-act (get-response url (keys response-exp) content-type as-type)]
        (pprint (get-response url [:headers]))
        (print-exp-act response-exp response-act)
        (t/is (= response-exp
                 response-act)))
      ;;
      (new-test "get-todo-test: empty body from todo not in state is 404")
      (let [url (sut->url sut (url-for :get-todo {:path-params {:todo-id (random-uuid)}}))
            response-exp {:body ""
                          :status not-found-code}
            response-act (get-response url (keys response-exp))]
        (pprint (get-response url))
        (print-exp-act response-exp response-act)
        (t/is (= response-exp
                 response-act))))))

(t/deftest post-todo-test
  (let [todo-id (str (random-uuid))
        todo {:id todo-id
              :name "my test todo list"
              :items [{:id (str (random-uuid))
                       :name "finish the test"}]}]
    (with-system
      [sut (core/real-world-clojure-api-system (m-config))]
      (reset! (-> sut :in-memory-state-component :state-atom)
              [todo])
      ;;
      (new-test "post-todo-test: created todo added to state")
      (let [url (sut->url sut (url-for :post-todo))
            content-type :json
            as-type :json
            response-exp {:body (json/encode todo)
                          :status created-code}
            response-act (get-response url (keys response-exp) content-type as-type)]
        (pprint (get-response url [:headers]))
        (print-exp-act response-exp response-act)
        (t/is (= response-exp
                 response-act))))))

(t/deftest content-negotiation-test
  (with-system
    [sut (core/real-world-clojure-api-system (m-config))]
    ;;
    (new-test "content-negotiation-test: json shall be accepted")
    (let [url (sut->url sut (url-for :greet))
          content-type :json
          response-exp {:body "Hi Youtube"
                        :status ok-code}
          response-act (get-response url (keys response-exp) content-type)]
      (pprint (get-response url [:headers]))
      (print-exp-act response-exp response-act)
      (t/is (= response-exp
               response-act)))
    ;;
    (new-test "content-negotiation-test: edn shall be rejected")
    (let [url (sut->url sut (url-for :greet))
          content-type :edn
          response-exp {:body not-acceptable-reason
                        :status not-acceptable-code}
          response-act (get-response url (keys response-exp) content-type)]
      (pprint (get-response url [:headers]))
      (print-exp-act response-exp response-act)
      (t/is (= response-exp
               response-act)))))