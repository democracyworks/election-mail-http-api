(ns election-mail-http-api.service
  (:require [io.pedestal.http :as bootstrap]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.route.definition :refer [defroutes]]
            [io.pedestal.interceptor :refer [interceptor]]
            [ring.util.response :as ring-resp]
            [turbovote.resource-config :refer [config]]
            [pedestal-toolbox.params :refer :all]
            [pedestal-toolbox.cors :as cors]
            [pedestal-toolbox.content-negotiation :refer :all]
            [kehaar.core :as k]
            [clojure.core.async :refer [go alt! timeout]]
            [bifrost.core :as bifrost]
            [bifrost.interceptors :as bifrost.i]
            [election-mail-http-api.channels :as channels]
            [clojure.tools.logging :as log]))

(def ping
  (interceptor
   {:enter
    (fn [ctx]
      (assoc ctx :response (ring-resp/response "OK")))}))

(defroutes routes
  [[["/"
     ^:interceptors [(body-params)
                     (negotiate-response-content-type ["application/edn"
                                                       "application/transit+json"
                                                       "application/transit+msgpack"
                                                       "application/json"
                                                       "text/plain"])]
     ["/ping" {:get [:ping ping]}]
     ["/subscriptions/:user-id"
      ^:interceptors [(bifrost.i/update-in-request
                       [:path-params :user-id]
                       #(java.util.UUID/fromString %))
                      (bifrost.i/update-in-response
                       [:body :subscription]
                       [:body] identity)]
      {:get [:subscription-read
             (bifrost/interceptor
              channels/subscription-read
              (config [:timeouts :subscription-read]))]
       :put [:subscription-create
             (bifrost/interceptor
              channels/subscription-create
              (config [:timeouts :subscription-create]))]
       :delete [:subscription-delete
                (bifrost/interceptor
                 channels/subscription-delete
                 (config [:timeouts :subscription-delete]))]}]
     ["/mailing-forms" {:put [:mailing-forms
                              (bifrost/interceptor
                               channels/mailing-forms
                               (config [:timeouts :mailing-forms]))]}]]]])

(defn service []
  (let [allowed-origins (config [:server :allowed-origins])]
    (log/debug "Allowed Origins Config: " (pr-str allowed-origins))
    {::env :prod
     ::bootstrap/router :linear-search
     ::bootstrap/routes routes
     ::bootstrap/resource-path "/public"
     ::bootstrap/allowed-origins (cors/domain-matcher-fn
                                  (map re-pattern allowed-origins))
     ::bootstrap/host (config [:server :hostname])
     ::bootstrap/type :immutant
     ::bootstrap/port (config [:server :port])}))
