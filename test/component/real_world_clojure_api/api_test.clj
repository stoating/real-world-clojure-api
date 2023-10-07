(ns component.real-world-clojure-api.api-test
  (:require [clojure.test :as t]
            [com.stuartsierra.component :as component]
            [real-world-clojure-api.core :as acore]
            [clj-http.client :as client]))

(defmacro with-system
  [[bound-var binding-expr] & body]
  `(let [~bound-var (component/start ~binding-expr)]
     (try
       ~@body
       (finally
         (component/stop ~bound-var)))))

(t/deftest greeting-test
  (with-system
    [sut (acore/real-world-clojure-api-system {:server {:port 8088}})]
    (t/is (= {:body "Hi Youtube"
              :status 200}
             (-> (str "http://localhost:" 8088 "/greet")
                 (client/get)
                 (select-keys [:body :status]))))))

 (t/deftest a-simple-api-test
   (t/is (= 1 1)))