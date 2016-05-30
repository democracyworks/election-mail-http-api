(ns election-mail-http-api.queue
  (:require [clojure.tools.logging :as log]
            [langohr.core :as rmq]
            [kehaar.core :as k]
            [kehaar.wire-up :as wire-up]
            [kehaar.rabbitmq]
            [election-mail-http-api.channels :as channels]
            [election-mail-http-api.handlers :as handlers]
            [turbovote.resource-config :refer [config]]))

(defn initialize []
  (let [max-retries 5
        rabbit-config (config [:rabbitmq :connection])
        connection (kehaar.rabbitmq/connect-with-retries rabbit-config max-retries)]
    (let [incoming-events []
          incoming-services [(wire-up/incoming-service
                              connection
                              "election-mail-http-api.ok"
                              (config [:rabbitmq :queues "election-mail-http-api.ok"])
                              channels/ok-requests
                              channels/ok-responses)]
          external-services [(wire-up/external-service
                              connection
                              ""
                              "election-mail-http-api.mailing.forms"
                              (config [:rabbitmq :queues "election-mail-http-api.mailing.forms"])
                              (config [:timeouts :mailing-forms])
                              channels/mailing-forms)

                             (wire-up/external-service
                              connection
                              ""
                              "election-mail-http-api.subscription.read"
                              (config [:rabbitmq :queues "election-mail-http-api.subscription.read"])
                              (config [:timeouts :subscription-read])
                              channels/subscription-read)

                             (wire-up/external-service
                              connection
                              ""
                              "election-mail-http-api.subscription.create"
                              (config [:rabbitmq :queues "election-mail-http-api.subscription.create"])
                              (config [:timeouts :subscription-create])
                              channels/subscription-create)

                             (wire-up/external-service
                              connection
                              ""
                              "election-mail-http-api.subscription.delete"
                              (config [:rabbitmq :queues "election-mail-http-api.subscription.delete"])
                              (config [:timeouts :subscription-delete])
                              channels/subscription-delete)]
          outgoing-events []]

      (wire-up/start-responder! channels/ok-requests
                                channels/ok-responses
                                handlers/ok)

      
      {:connections [connection]
       :channels (vec (concat
                       incoming-events
                       incoming-services
                       external-services
                       outgoing-events))})))

(defn close-resources! [resources]
  (doseq [resource resources]
    (when-not (rmq/closed? resource) (rmq/close resource))))

(defn close-all! [{:keys [connections channels]}]
  (close-resources! channels)
  (close-resources! connections))
