(ns election-mail-http-api.channels
  (:require [clojure.core.async :as async]))


(defonce ok-requests (async/chan))
(defonce ok-responses (async/chan))

(defonce mailing-forms (async/chan))

(defonce subscription-read (async/chan))
(defonce subscription-create (async/chan))
(defonce subscription-delete (async/chan))

(defn close-all! []
  (doseq [c [ok-requests ok-responses
             mailing-forms
             subscription-read
             subscription-create
             subscription-delete]]
    (async/close! c)))
