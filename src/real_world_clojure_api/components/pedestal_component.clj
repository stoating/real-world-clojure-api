(ns real-world-clojure-api.components.pedestal-component
  (:require [cheshire.core :as json]
            [com.stuartsierra.component :as component]
            [honey.sql :as sql]
            [io.pedestal.http :as http]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http.content-negotiation :as content-negotiation]
            [io.pedestal.http.route :as route]
            [io.pedestal.interceptor :as interceptor]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [schema.core :as s]))

(defn response
  ([status]
   (response status nil))
  ([status body]
   (merge {:status status
           :headers {"Content-Type" "application/json"}}
          (when body {:body (json/encode body)}))))

(def ok (partial response 200))
(def created (partial response 201))
(def not-found (partial response 404))

(defn get-todo-by-id
  [{:keys [in-memory-state-component]}
   todo-id]
  (->> @(:state-atom in-memory-state-component)
       (filter (fn [todo]
                 (= todo-id (:id todo))))
       (first)))

(def todo-handler-get
  {:name :todo-handler-get
   :enter
   (fn [{:keys [dependencies] :as context}]
     (let [request (:request context)
           todo (get-todo-by-id dependencies
                                (-> request
                                    :path-params
                                    :todo-id))
           response (if todo
                      (ok todo)
                      (not-found))]
       (assoc context :response response)))})

(def db-todo-handler-get
  {:name :db-todo-handler-get
   :enter
   (fn [{:keys [dependencies] :as context}]
     (let [{:keys [data-source]} dependencies
           todo-id (-> context
                       :request
                       :path-params
                       :todo-id
                       (parse-uuid))
           todo (jdbc/execute-one!
                 (data-source)
                 (-> {:select :*
                      :from :todo
                      :where [:= :todo-id todo-id]}
                     (sql/format))
                 {:builder-fn rs/as-unqualified-kebab-maps})
           response (if todo
                      (ok todo)
                      (not-found))]
       (assoc context :response response)))})

(def info-handler-get
  {:name :todo-handler-get
   :enter
   (fn [{:keys [dependencies] :as context}]
     (let [{:keys [data-source]} dependencies
           db-response (first (jdbc/execute!
                               (data-source)
                               ["SHOW SERVER_VERSION"]))]
       (assoc context :response {:status 200
                                  :body (str "Database server version " (:server_version db-response))})))})

(comment
  [{:id (random-uuid)
    :name "my todo list"
    :items [{:id (random-uuid)
             :name "make a new youtube video"
             :status :created}]}
   {:id (random-uuid)
    :name "empty todo list"
    :items []}])

(defn greet-handler-get
  [_]
  {:status 200
   :body "Hi Youtube"})

(defn save-todo!
  [{:keys [in-memory-state-component]} todo]
  (swap! (:state-atom in-memory-state-component) conj todo))

(s/defschema
  TodoItem
  {:id s/Str
   :name s/Str
   :status s/Str})

(s/defschema
  Todo
  {:id s/Str
   :name s/Str
   :items [TodoItem]})

(def todo-handler-post
  {:name :todo-handler-post
   :enter
   (fn [{:keys [dependencies] :as context}]
     (let [request (:request context)
           todo (s/validate Todo (:json-params request))]
       (save-todo! dependencies todo)
       (assoc context :response (created todo))))})

(def routes
  (route/expand-routes
   #{["/greet"            :get  greet-handler-get    :route-name :greet-get]
     ["/info"             :get  info-handler-get     :route-name :info-get]
     ["/todo/:todo-id"    :get  todo-handler-get     :route-name :todo-get]
     ["/todo"             :post [(body-params/body-params) todo-handler-post] :route-name :todo-post]
     ["/db/todo/:todo-id" :get  db-todo-handler-get  :route-name :db-todo-get]}))

(def url-for (route/url-for-routes routes))

(defn inject-dependencies
  [dependencies]
  (interceptor/interceptor
   {:name ::inject-dependencies
    :enter (fn [context]
             (assoc context :dependencies dependencies))}))

(def content-negotiation-interceptor
  (content-negotiation/negotiate-content
   ["application/json"]))

(defrecord PedestalComponent
           [config
            example-component
            in-memory-state-component]
  component/Lifecycle

  (start [component]
    (println "Starting PedestalComponent")
    (let [server (-> {::http/routes routes
                      ::http/type   :jetty
                      ::http/join?  false
                      ::http/port   (-> config :server :port)}
                     (http/default-interceptors)
                     (update ::http/interceptors concat
                             [(inject-dependencies component)
                              content-negotiation-interceptor])
                     (http/create-server)
                     (http/start))]
      (assoc component :server server)))

  (stop [component]
    (println "Stopping PedestalComponent")
    (when-let [server (:server component)]
      (http/stop server))
    (assoc component :server nil)))

(defn new-pedestal-component
  [config]
  (map->PedestalComponent {:config config}))