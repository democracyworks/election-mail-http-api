(ns election-mail-http-api.service-test
  (:require [election-mail-http-api.server :as server]
            [election-mail-http-api.channels :as channels]
            [clj-http.client :as http]
            [clojure.edn :as edn]
            [cognitect.transit :as transit]
            [clojure.core.async :as async]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [bifrost.core :as bifrost])
  (:import [java.io ByteArrayInputStream]))

(def test-server-port 65342)

(defn run-test-server [run-tests]
  (let [service-map
        (server/start-http-server {:io.pedestal.http/port test-server-port})]
    (run-tests)
    (io.pedestal.http/stop service-map)))

(use-fixtures :once run-test-server)

(def root-url (str "http://localhost:" test-server-port))

(deftest ping-test
  (testing "ping responds with 'OK'"
    (let [response (http/get (str root-url "/ping")
                             {:headers {:accept "text/plain"}})]
      (is (= 200 (:status response)))
      (is (= "OK" (:body response))))))

(deftest create-subscription-test
  (testing "PUT to /subscriptions/:user-id puts appropriate create message
            on subscription-create channel"
    (let [fake-user-id (java.util.UUID/randomUUID)
          http-response-ch (async/thread
                             (http/put (str/join "/"
                                                 [root-url
                                                  "subscriptions"
                                                  fake-user-id])
                                       {:headers {:accept "application/edn"}
                                        :form-params {:subscribe true}
                                        :content-type :edn}))
          [response-ch message] (async/alt!!
                                  channels/subscription-create ([v] v)
                                  (async/timeout 1000) [nil ::timeout])]
      (assert (not= message ::timeout))
      (is (= {:user-id fake-user-id
              :subscribe true}
             message))
      (async/>!! response-ch {:status :ok
                              :subscription {:user-id fake-user-id
                                             :subscribed true}})
      (let [http-response (async/alt!! http-response-ch ([v] v)
                                       (async/timeout 1000) ::timeout)]
        (assert (not= http-response ::timeout))
        (is (= 200 (:status http-response)))
        (is (= {:user-id fake-user-id, :subscribed true}
               (-> http-response :body edn/read-string))))))
  (testing "PUT to /subscriptions/:user-id can respond with Transit"
    (let [fake-user-id (java.util.UUID/randomUUID)
          http-response-ch (async/thread
                             (http/put
                              (str/join "/" [root-url
                                             "subscriptions"
                                             fake-user-id])
                              {:headers {:accept "application/transit+json"}
                               :form-params {:subscribe true}
                               :content-type :edn}))
          [response-ch message] (async/alt!! channels/subscription-create ([v] v)
                                             (async/timeout 1000) [nil ::timeout])]
      (assert (not= message ::timeout))
      (async/>!! response-ch {:status :ok
                              :subscription {:user-id fake-user-id
                                              :subscribed true}})
      (let [http-response (async/alt!! http-response-ch ([v] v)
                                       (async/timeout 1000) ::timeout)
            transit-in (ByteArrayInputStream. (-> http-response
                                                  :body
                                                  (.getBytes "UTF-8")))
            transit-reader (transit/reader transit-in :json)
            create-data (transit/read transit-reader)]
        (assert (not= http-response ::timeout))
        (is (= fake-user-id (:user-id message)))
        (is (:subscribe message))
        (is (= 200 (:status http-response)))
        (is (= {:user-id fake-user-id, :subscribed true} create-data)))))
  (testing "error from backend service results in HTTP server error response"
    (let [fake-user-id (java.util.UUID/randomUUID)
          http-response-ch (async/thread
                             (http/put (str/join "/" [root-url
                                                      "subscriptions"
                                                      fake-user-id])
                                       {:headers {:accept "application/edn"}
                                        :form-params {:subscribe true}
                                        :content-type :edn
                                        :throw-exceptions false}))
          [response-ch message] (async/alt!! channels/subscription-create ([v] v)
                                             (async/timeout 1000) [nil ::timeout])]
      (assert (not= message ::timeout))
      (async/>!! response-ch {:status :error
                              :error {:type :server}})
      (let [http-response (async/alt!! http-response-ch ([v] v)
                                       (async/timeout 1000) ::timeout)]
        (assert (not= http-response ::timeout))
        (is (= 500 (:status http-response))))))
  (testing "no response from backend service results in HTTP gateway timeout error response"
    (let [fake-user-id (java.util.UUID/randomUUID)
          http-response-ch (async/thread
                             (http/put (str/join "/" [root-url
                                                      "subscriptions"
                                                      fake-user-id])
                                       {:headers {:accept "application/edn"}
                                        :form-params {:subscribe true}
                                        :content-type :edn
                                        :throw-exceptions false}))
          [response-ch message] (async/alt!! channels/subscription-create ([v] v)
                                             (async/timeout 1000) [nil ::timeout])]
      (assert (not= message ::timeout))
      (let [http-response (async/alt!! http-response-ch ([v] v)
                                       (async/timeout 11000) ::timeout)]
        (assert (not= http-response ::timeout))
        (is (= 504 (:status http-response)))))))

(deftest delete-subscription-test
  (testing "DELETE to /subscriptions/:user-id puts appropriate create message
            on subscription-delete channel"
    (let [fake-user-id (java.util.UUID/randomUUID)
          http-response-ch (async/thread
                             (http/delete (str/join "/"
                                                    [root-url
                                                     "subscriptions"
                                                     fake-user-id])
                                          {:headers {:accept "application/edn"}}))
          [response-ch message] (async/alt!!
                                  channels/subscription-delete ([v] v)
                                  (async/timeout 1000) [nil ::timeout])]
      (assert (not= message ::timeout))
      (is (= {:user-id fake-user-id}
             message))
      (async/>!! response-ch {:status :ok})
      (let [http-response (async/alt!! http-response-ch ([v] v)
                                       (async/timeout 1000) ::timeout)]
        (assert (not= http-response ::timeout))
        (is (= 200 (:status http-response)))))))

(deftest read-subscription-test
  (testing "GET to /subscriptions/:user-id puts appropriate read message
            on subscription-read channel"
    (let [fake-user-id (java.util.UUID/randomUUID)
          http-response-ch (async/thread
                             (http/get (str/join "/" [root-url
                                                      "subscriptions"
                                                      fake-user-id])
                                       {:headers {:accept "application/edn"}}))
          [response-ch message] (async/alt!! channels/subscription-read ([v] v)
                                             (async/timeout 1000) [nil ::timeout])]
      (assert (not= message ::timeout))
      (async/>!! response-ch {:status :ok
                              :subscription {:user-id fake-user-id
                                             :subscribed true}})
      (let [http-response (async/alt!! http-response-ch ([v] v)
                                       (async/timeout 1000) ::timeout)]
        (assert (not= http-response ::timeout))
        (is (= fake-user-id (:user-id message)))
        (is (= 200 (:status http-response)))
        (is (= {:user-id fake-user-id, :subscribed true}
               (-> http-response :body edn/read-string)))))))

(deftest test-mailing-forms
  (testing "PUT to /mailing-forms puts the appropriate message on the
            mailing-forms channel"
    (let [forms-data
          {:user {:first-name "Fake"
                  :last-name "User"
                  :addresses {:registered {:street "1507 Blake St"
                                           :city "Denver"
                                           :state "CO"
                                           :zip "80202"}}}
           :registration-form-uri "http://fake.url.com"
           :registration-authority {:office-name "Election Office"
                                    :physical-address
                                    {:street "1500 Larimer St"
                                     :city "Denver"
                                     :state "CO"
                                     :zip "80202"}}
           :id-instructions "Fill in your ID number"}
          http-response-ch (async/thread
                             (http/put (str/join "/" [root-url
                                                      "mailing-forms"])
                                       {:headers {:accept "application/edn"}
                                        :form-params forms-data
                                        :content-type :edn}))
          [response-ch message] (async/alt!!
                                  channels/mailing-forms ([v] v)
                                  (async/timeout 1000) [nil ::timeout])]
      (assert (not= message ::timeout))
      (async/>!! response-ch {:status :ok})
      (let [http-response (async/alt!! http-response-ch ([v] v)
                                       (async/timeout 1000) ::timeout)]
        (assert (not= http-response ::timeout))
        (is (= forms-data message))
        (is (= 200 (:status http-response)))))))
