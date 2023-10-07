(ns component.real-world-clojure-api.api-test
  (:require [clojure.test :as t]
            [com.stuartsierra.component :as component]
            [real-world-clojure-api.core :as core]
            [clj-http.client :as client]))

;; declare var which will be introduced after executing 'with-system' macro
(declare sut)

;; high-level defines
(def m-config {:server {:address "http://localhost:"
                        :port 8088}})
(def http-status {:ok 200})

;; low-level defines
(def m-address (get-in m-config [:server :address]))
(def m-port (get-in m-config [:server :port]))
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

(t/deftest greeting-test
  (let [path "greet"
        response-exp {:body "Hi Youtube"
                      :status http-ok}
        url (str m-address m-port "/" path)]
    (with-system
      [sut (core/real-world-clojure-api-system m-config)]
      (t/testing "expected hard-coded body is returned for the greeting")
      (t/is (= response-exp
               (response-act url (keys response-exp)))))))

(t/deftest todo-test
  (let [path "todo"

        ; test 1
        todo-id-1 (random-uuid)
        todo-1 {:id todo-id-1
                :name "my test todo list"
                :items [{:id (random-uuid)
                         :name "finish the test"}]}
        url-1 (str m-address m-port "/" path "/" todo-id-1)
        response-exp-1 {:body (pr-str todo-1)
                        :status http-ok}

        ; test 2
        url-2 (str m-address m-port "/" path "/" (random-uuid))
        response-exp-2 {:body ""
                        :status http-ok}]
    (with-system
      [sut (core/real-world-clojure-api-system m-config)]
      (reset! (-> sut :in-memory-state-component :state-atom)
              [todo-1])
      (t/testing "expected body is returned for the todo we set in state")
      (t/is (= response-exp-1
               (response-act url-1 (keys response-exp-1))))
      (t/testing "empty body is returned for some other todo uuid")
      (t/is (= response-exp-2
               (response-act url-2 (keys response-exp-2)))))))

(t/deftest a-simple-api-test
  (t/is (= 1 1)))