(ns component.real-world-clojure-api.api-test
  (:require [clj-http.client :as client]
            [com.stuartsierra.component :as component]
            [real-world-clojure-api.core :as core]
            [real-world-clojure-api.components.pedestal-component :refer [url-for]]
            [clojure.string :as str]
            [clojure.test :as t])
  (:import [java.net ServerSocket]))

;; declare var which will be introduced after executing 'with-system' macro
(declare sut)

;; high-level defines
(def localhost "http://localhost:")
(def http-status {:ok 200})

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
(def http-ok (get-in http-status [:ok]))


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
       (select-keys keys))))

(defn sut->url
  [sut path]
  (str/join [m-address
             (-> sut :pedestal-component :config :server :port)
             path]))

(t/deftest greeting-test
  (let [response-exp {:body "Hi Youtube"
                      :status http-ok}]
    (with-system
      [sut (core/real-world-clojure-api-system (m-config))]
      (t/testing "expected hard-coded body is returned for the greeting")
      (let [url (sut->url sut (url-for :greet))]
        (t/is (= response-exp
                 (response-act url (keys response-exp))))))))

(t/deftest todo-test
  (let [todo-id (random-uuid)
        todo {:id todo-id
              :name "my test todo list"
              :items [{:id (random-uuid)
                       :name "finish the test"}]}]
    (with-system
      [sut (core/real-world-clojure-api-system (m-config))]
      (reset! (-> sut :in-memory-state-component :state-atom)
              [todo])
      ;;
      ;;
      (t/testing "expected body is returned for the todo we set in state")
      (let [url (sut->url sut (url-for :todo {:path-params {:todo-id todo-id}}))
            response-exp {:body (pr-str todo)
                          :status http-ok}]
        (t/is (= response-exp
                 (response-act url (keys response-exp)))))
      ;;
      ;;
      (t/testing "empty body is returned for some other todo uuid")
      (let [url (sut->url sut (url-for :todo {:path-params {:todo-id (random-uuid)}}))
            response-exp {:body ""
                          :status http-ok}]
        (t/is (= response-exp
                 (response-act url (keys response-exp))))))))

(t/deftest a-simple-api-test
  (t/is (= 1 1)))