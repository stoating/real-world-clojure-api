(ns unit.real-world-clojure-api.simple-test
  (:require [clojure.test :as t]
            [real-world-clojure-api.components.pedestal-component :refer [url-for]]))

(t/deftest a-simple-passing-test
  (t/is (= 1 1)))

(t/deftest url-for-test
  (t/testing "greet endpoint url"
    (t/is (= "/greet" (url-for :greet))))
  (t/testing "todo by id endpoint url"
    (let [todo-id (random-uuid)]
      (t/is (= (str "/todo/" todo-id)
               (url-for :todo {:path-params {:todo-id todo-id}}))))))