(ns org.martinklepsch.confetti.cloudfront
  (:require [amazonica.aws.cloudfront :as cf]
            [clojure.tools.logging :as log]))

;; Cloudfront ===================================================================

(defn cloudfront-origin [origin-id s3-endpoint]
  {:id origin-id
   :domain-name s3-endpoint
   ;:origin-path ""
   :s3origin-config {:origin-access-identity ""}})

(defn cache-behavior [origin-id]
  {:target-origin-id origin-id
   :default-ttl 86400,
   :forwarded-values {:headers {:items [], :quantity 0},:cookies {:forward "all"}, :query-string true},
   :smooth-streaming false,
   :viewer-protocol-policy "allow-all",
   :max-ttl 31536000,
   :allowed-methods {:items ["HEAD" "GET"], :quantity 2, :cached-methods {:items ["HEAD" "GET"], :quantity 2}},
   :min-ttl 0,
   :trusted-signers {:enabled false, :items [], :quantity 0}})

(defn create-distribution [cred root-domain]
  (let [oid (str "confetti-" root-domain)]
    (cf/create-distribution
     cred
     :distribution-config
     {:enabled true
      :comment (str "Created by Confetti for project: " root-domain)
      :default-cache-behavior (cache-behavior oid) 
      :origins {:items [(cloudfront-origin oid (str root-domain ".co.s3.amazonaws.com"))]
                :quantity 1}
      :aliases {:items [(str "www." root-domain) root-domain], :quantity 2}
      :caller-reference (str (java.util.UUID/randomUUID))})))

(defn update-distribution-config [id update-fn]
  (let [{:keys [distribution-config etag]} (cf/get-distribution-config :id id)
        new  (update-fn distribution-config)]
    (cf/update-distribution :id "E3W3ZRRVTS9INR"
                            :distribution-config new
                            :if-match etag)))

(defn delete-distribution [id]
  (let [{:keys [etag]} (cf/get-distribution-config :id id)]
    (cf/delete-distribution :id id :if-match etag)))
