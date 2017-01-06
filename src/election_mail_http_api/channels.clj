(ns election-mail-http-api.channels
  (:require [clojure.core.async :as async]))

(defonce mailing-forms (async/chan))

(defonce subscription-read (async/chan))
(defonce subscription-create (async/chan))
(defonce subscription-delete (async/chan))

(defn close-all! []
  (doseq [c [mailing-forms
             subscription-read
             subscription-create
             subscription-delete]]
    (async/close! c)))
