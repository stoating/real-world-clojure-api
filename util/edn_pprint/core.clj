(ns edn-pprint.core
  (:require [clojure.pprint :refer [pprint]]
            [clojure.string :as s]))

(def tag-object-re #"\#[a-zA-Z0-9]*")

(-> (slurp "util/edn_pprint/input.edn")
    (s/replace tag-object-re "")
    (read-string)
    (pprint))