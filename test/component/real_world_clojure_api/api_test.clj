(ns component.real-world-clojure-api.api-test
  (:require [clj-http.client :as client]
            [com.stuartsierra.component :as component]
            [real-world-clojure-api.core :as api-test-core]
            [real-world-clojure-api.components.pedestal-component :refer [url-for]]
            [clojure.string :as str]
            [clojure.test :as t])
  (:import [java.net ServerSocket]))

(defn long-str [& strings] (clojure.string/join "\n" strings))
(defn new-test
  [s]
  (print (long-str
          "*******************************"
          s
          "*******************************"
          "")))
(defn print-exp-act
  [exp act]
  (println "exp:" (pr-str exp))
  (println "act:" (pr-str act)))

;; declare var which will be introduced after executing 'with-system' macro
(declare sut)

;; high-level defines
(def localhost "http://localhost:")
(def status {:ok {:code 200
                  :reason "OK"}
             :not-acceptable {:code 406
                              :reason "Not Acceptable"}})

(def ok-code (get-in status [:ok :code]))
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

(defn response-act
  ([url keys]
   (-> url
       (client/get)
       (select-keys keys)))
  ([url keys content-type]
   (-> url
       (client/get {:accept content-type
                    :throw-exceptions false})
       (select-keys keys))))

(defn sut->url
  [sut path]
  (str/join [m-address
             (-> sut :pedestal-component :config :server :port)
             path]))

(t/deftest greeting-test
  (with-system
    [sut (api-test-core/real-world-clojure-api-system (m-config))]
    (new-test "expected hard-coded body shall be returned for the greeting")
    (let [url (sut->url sut (url-for :greet))
          response-exp {:body "Hi Youtube"
                        :status ok-code}
          response-act (response-act url (keys response-exp))]
      (print-exp-act response-exp response-act)
      (t/is (= response-exp
               response-act)))))

(t/deftest todo-test
  (let [todo-id (random-uuid)
        todo {:id todo-id
              :name "my test todo list"
              :items [{:id (random-uuid)
                       :name "finish the test"}]}]
    (with-system
      [sut (api-test-core/real-world-clojure-api-system (m-config))]
      (reset! (-> sut :in-memory-state-component :state-atom)
              [todo])
      ;;
      (new-test "expected body shall be returned for the todo we set in state")
      (let [url (sut->url sut (url-for :todo {:path-params {:todo-id todo-id}}))
            response-exp {:body (pr-str todo)
                          :status ok-code}
            response-act (response-act url (keys response-exp))]
        (print-exp-act response-exp response-act)
        (t/is (= response-exp
                 response-act)))
      ;;
      (new-test "empty body shall be returned for some other todo uuid")
      (let [url (sut->url sut (url-for :todo {:path-params {:todo-id (random-uuid)}}))
            response-exp {:body ""
                          :status ok-code}
            response-act (response-act url (keys response-exp))]
        (print-exp-act response-exp response-act)
        (t/is (= response-exp
                 response-act))))))

(t/deftest content-negotiation-test
  (with-system
    [sut (api-test-core/real-world-clojure-api-system (m-config))]
    ;;
    (new-test "json shall be accepted")
    (let [url (sut->url sut (url-for :greet))
          content-type :json
          response-exp {:body "Hi Youtube"
                        :status ok-code}
          response-act (response-act url (keys response-exp) content-type)]
      (print-exp-act response-exp response-act)
      (t/is (= response-exp
               response-act)))
    ;;
    (new-test "edn shall be not accepted")
    (let [url (sut->url sut (url-for :greet))
          content-type :edn
          response-exp {:body not-acceptable-reason
                        :status not-acceptable-code}
          response-act (response-act url (keys response-exp) content-type)]
      (print-exp-act response-exp response-act)
      (t/is (= response-exp
               response-act)))))