(ns real-world-clojure-api.components.pedestal-component
  (:require [com.stuartsierra.component :as component]
            [io.pedestal.http.content-negotiation :as content-negotiation]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.interceptor :as interceptor]
            [cheshire.core :as json]))

(defn response
  ([status]
   (response status nil))
  ([status body]
   (merge {:status status
           :body body
           :headers {"Content-Type" "application/json"}}
          (when body {:body body}))))

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

(def get-todo-handler
  {:name :get-todo-handler
   :enter
   (fn [{:keys [dependencies] :as context}]
     (let [request (:request context)
           todo (get-todo-by-id dependencies
                                (-> request
                                    :path-params
                                    :todo-id))
           response (if todo
                      (ok (json/encode todo))
                      (not-found))]
       (assoc context :response response)))})

(comment
  [{:id (random-uuid)
    :name "my todo list"
    :items [{:id (random-uuid)
             :name "make a new youtube video"
             :status :created}]}
   {:id (random-uuid)
    :name "empty todo list"
    :items []}])

(defn greet-handler
  [_]
  {:status 200
   :body "Hi Youtube"})

(defn save-todo!
  [{:keys [in-memory-state-component]}
   todo]
  (swap! (:state-atom in-memory-state-component)
         conj todo))

(def post-todo-handler
  {:name :post-todo-handler
   :enter
   (fn [{:keys [dependencies] :as context}]
     (let [request (:request context)
           todo {}]
       (save-todo! dependencies todo)
       (assoc context :response (created todo))))})

(def routes
  (route/expand-routes
   #{["/greet"         :get  greet-handler     :route-name :greet]
     ["/todo/:todo-id" :get  get-todo-handler  :route-name :get-todo]
     ["/todo"          :post post-todo-handler :route-name :post-todo]}))

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