(ns org.martinklepsch.confetti.lifecycle
  (:require [amazonica.aws.s3 :as s3]
            [amazonica.aws.cloudfront :as cf]
            [amazonica.aws.route53 :as r53]
            [amazonica.aws.identitymanagement :as iam])
  (:import [com.amazonaws.services.cloudfront.model CreateDistributionRequest]))

(defn ^:private uuid []
  (str (java.util.UUID/randomUUID)))

(def cloudfront-default-config
  {:enabled false
   :default-root-object "index.html"
   :origins {:quantity 1 :items [{:id "default-origin"
                                  :domain-name "example.com.s3.amazonaws.com"
                                        ;:origin-path ""
                                  :s3origin-config {:origin-access-identity ""}}]}
   :logging {:enabled false
             :include-cookies false
             :bucket "abcd1234.s3.amazonaws.com"
             :prefix "cflog_"}
   :caller-reference 12345
   :aliases {:items [] :quantity 0}
   :cache-behaviors {:quantity 0 :items []}
   :comment "Created by confetti"
   :default-cache-behavior
   {:target-origin-id "default-origin"
    :forwarded-values {:query-string false :cookies {:forward "none"}}
    :trusted-signers {:enabled false :quantity 0}
    :min-ttl 3600
    :viewer-protocol-policy "allow-all"}
   :price-class "PriceClass_All"})

(defprotocol AWSLifecycle
  (-create! [model opts])
  (-get-config [model])
  (-set-config! [model new-config])
  (-update-config! [model update-fn])
  (-destroy! [model]))

(defrecord S3Bucket [name])
(defrecord CloudfrontDistribution [id])
(defrecord Route53HostedZone [name])

(extend-protocol AWSLifecycle
  S3Bucket
  (-create! [{:keys [name]} opts]
    )
  (-destroy! [{:keys [name]}]
    (s3/delete-bucket name)))

(extend-protocol AWSLifecycle
  CloudfrontDistribution
  (-create! [model {:keys [config] :or {config cloudfront-default-config}}]
    (let [dist (cf/create-distribution :distribution-config config)]
      (map->CloudfrontDistribution {:id   (-> dist :distribution :id)
                                    :etag (-> dist :etag)})))

  (-get-config [{:keys [id]}]
    (cf/get-distribution-config :id id))

  (-set-config! [{:keys [id etag]} new-config]
    (let [updated (cf/update-distribution :id id
                                          :distribution-config new-config
                                          :if-match etag)]
      (map->CloudfrontDistribution {:id   (-> updated :distribution :id)
                                    :etag (-> updated :etag)})))

  (-update-config! [dist upd-fn]
    (let [{:keys [etag distribution-config]} (-get-config dist)]
      (-set-config! (map->CloudfrontDistribution {:id (:id dist) :etag etag})
                    (upd-fn distribution-config))))

  (-destroy! [{:keys [id etag]}]
    (let [etag (or etag (:etag (cf/get-distribution-config :id id)))]
      (cf/delete-distribution :id id :if-match etag))))

(-create! (->CloudfrontDistribution "x") {})
(-destroy! (map->CloudfrontDistribution {:id "EZ7XTYQOBI10S", :etag "E2UR1R4QSNBJ6Y"}))
(-update-config! (->CloudfrontDistribution "E18XE3JAJZHS4G") #(assoc % :enabled false))
(-destroy! (->CloudfrontDistribution "E18XE3JAJZHS4G"))

;; (defn mk-bucket
;;   [name opts]
;;   (s3/create-bucket name)
;;   (->S3Bucket name))

;; (defn mk-distribution
;;   [{:keys [config] :as opts
;;     :or {:config cloudfront-default-config}}]
;;   (let [d (cf/create-distribution :distribution-config config)]
;;     (->CloudfrontDistribution (-> d :distribution :id)
;;                               #_(-> d :etag))))

;; (defn mk-hosted-zone
;;   [name opts]
;;   (try (r53/create-hosted-zone :name name :caller-reference (uuid))
;;        (->Route53HostedZone name)))

;;(cf/get-distribution-config :id "E18XE3JAJZHS4G")
