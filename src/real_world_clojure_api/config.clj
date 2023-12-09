(ns real-world-clojure-api.config
  (:require [aero.core :as aero]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.string :as str]
            [malli.core :as m]
            [malli.error :as me]
            [malli.util :as mu]))

(defn render-config
  []
  {:server {:port (parse-long
                   (or (System/getenv "RWCA_HTMX_SERVER_PORT")
                       "8080"))}})

(defn read-config
  []
  (-> "config.edn"
      (io/resource)
      (aero/read-config)))

;; extend aero (check the config.edn)
(defmethod aero/reader 'csv-set
  [_opts _tag value]
  (if (str/blank? value)
      #{}
      (->> (str/split value #",")
           (remove str/blank?)
           (map str/trim)
           #_(map keyword)
           (into #{}))))


(def kafka-config-base-schema
  (m/schema
   [:map
    [:bootstrap.servers :string]
    [:application.id :string]
    [:auto.offset.reset [:enum "earliest" "latest"]]
    [:producer.acks [:enum "0" "1" "all"]]]))

(def kafka-config-schema
  (m/schema
   [:multi {:dispatch :security.protocol} ;; dispatch on this key
    ;; if ssl, use this schema
    ["SSL"
     (mu/merge
      kafka-config-base-schema
      [:map
       [:security.protocol [:enum "SSL"]]
       [:ssl.keystore.type [:enum "PKCS12"]]
       [:ssl.truststore.type [:enum "JKS"]]
       [:ssl.keystore.location :string]
       [:ssl.keystore.password :string]
       [:ssl.key.password :string]
       [:ssl.truststore.location :string]
       [:ssl.truststore.password :string]])]
   ;; if plaintext, use this schema
    ["PLAINTEXT"
     (mu/merge
      kafka-config-base-schema
      [:map
       [:security.protocol [:enum "PLAINTEXT"]]])]]))

;; {:server
;;  {:port #long #or [#env REAL_WORLD_CLOJURE_API_SERVER_PORT 8080]}
;;  :htmx
;;  {:server
;;   {:port #long #or [#env RWCA_HTMX_SERVER_PORT 8081]}}
;;  :input-topics #csv-set #env RWCA_INPUT_TOPICS}

;; config schema
(def config-schema
  (m/schema
   [:map
    [:server [:map
              [:port [:int {:min 1
                            :max 10000}]]]]
    [:htmx [:map
            [:server [:map
                      [:port [:int {:min 1
                                    :max 10000}]]]]]]
    [:input-topics [:set :string]]
    [:kafka kafka-config-schema]]))

(def valid-config?
  (m/validator config-schema))

(defn assert-valid-config!
  [config]
  (if (valid-config? config)
    config
    (->> {:error (me/humanize (m/explain config-schema config))}
         (ex-info "Config is invalid")
         (throw))))

(comment
  (let [config {:server {:port 8080}
                :htmx {:server {:port 3000}}
                :input-topics #{"a" "topic-1"}
                :kafka {:bootstrap.servers "kafka",
                        :application.id "my-app",
                        :auto.offset.reset "earliest",
                        :producer.acks "all",
                        :ssl.keystore.type "PKCS12",
                        :ssl.truststore.type "JKS",
                        :ssl.keystore.location "keystore-location",
                        :ssl.keystore.password "keystore-password",
                        :ssl.key.password "key-password",
                        :ssl.truststore.location "truststore-location",
                        :ssl.truststore.password "truststore-password",
                        :security.protocol "SSL"}}]
    (assert-valid-config! config)))

(comment
  (-> config-schema
      (m/explain
       {:server {:port 8080}
        :htmx {:server {:port 3000}}
        :input-topics #{"a" "topic-1"}
        :kafka {:bootstrap.servers "kafka",
                :application.id "my-app",
                :auto.offset.reset "earliest",
                :producer.acks "all",
                :ssl.keystore.type "PKCS12",
                :ssl.truststore.type "JKS",
                :ssl.keystore.location "keystore-location",
                :ssl.keystore.password "keystore-password",
                :ssl.key.password "key-password",
                :ssl.truststore.location "truststore-location",
                :ssl.truststore.password "truststore-password",
                :security.protocol "SSL"}})
      (me/humanize))
  ;;
  (-> kafka-config-schema
      (m/explain
       {:bootstrap.servers "kafka",
        :application.id "my-app",
        :auto.offset.reset "earliest",
        :producer.acks "all",
        :security.protocol "PLAINTEXT"})
      (me/humanize))
  ;;
  (-> kafka-config-schema
      (m/explain
       {;; base schema
        :bootstrap.servers "kafka",
        :application.id "my-app",
        :auto.offset.reset "earliest",
        :producer.acks "all",
        ;; ssl schema
        :ssl.keystore.type "PKCS12",
        :ssl.truststore.type "JKS",
        :ssl.keystore.location "keystore-location",
        :ssl.keystore.password "keystore-password",
        :ssl.key.password "key-password",
        :ssl.truststore.location "truststore-location",
        :ssl.truststore.password "truststore-password",
        :security.protocol "SSL"})
      (me/humanize))
  ;;
  (-> [:map
       [:id :string]
       [:count :int]]
      (m/explain {})
      (me/humanize)) ;; {:id ["missing required key"], :count ["missing required key"]}
  ;;
  (-> [:map
       [:id :string]
       [:count :int]]
      (m/explain {:id "arst"
                  :count 42})
      (me/humanize)) ;; nil
  ;;
  (-> [:map
       [:id :string]
       [:count :int]]
      (m/validate {:id "arst"
                   :count 42})) ;; true
  ;;
  (-> [:map {:closed true} ;; look at this! be strict
       [:id :string]
       [:count :int]
       [:an-optional-key {:optional true} :keyword]]
      (m/explain {:id "arst"
                  :count 42
                  :closed-map-oops "oops"})
      (me/humanize)) ;; disallowed key: :closed-map-oops
  ;;
  (-> [:map {:closed true} ;; look at this! be strict
       [:id :string]
       [:count :int]
       [:an-optional-key {:optional true} :keyword]]
      (m/explain {:id "arst"
                  :count 42})
      (me/humanize)) ;; nil
  ;;
  (-> [:map {:closed true} ;; look at this! be strict
       [:id :string]
       [:count :int]
       [:an-optional-key {:optional true} :keyword]]
      (m/explain {:id "arst"
                  :count 42
                  :an-optional-key :arst})
      (me/humanize)) ;; nil
  ;;
  (m/validate config-schema {})
  ;;
  (render-config)
  ;;
  (pprint/pprint (read-config))
  ;;
  ;; from your settings.json
  (System/getenv "RWCA_HTMX_SERVER_PORT")
  ;;
  (System/getenv "RWCA_INPUT_TOPICS")
  ;;
  (aero/reader {} 'csv-set "a,b, c,,d")
  )

