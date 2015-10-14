(ns org.martinklepsch.confetti.s3
  (:require [amazonica.aws.s3 :as s3]
            [clojure.pprint   :refer [pprint]]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]))

(defn allow-statement [actions resources]
  {:Effect   "Allow"
   :Action   actions
   :Resource resources})

(defn user-policy [bucket-name]
  {:Version "2012-10-17"
   :Statement [(allow-statement ["s3:ListBucket"] [(str "arn:aws:s3:::" bucket-name)])
                (allow-statement ["*"] [(str "arn:aws:s3:::" bucket-name "/*")])]})

(defn bucket-policy [bucket-name]
  {:Version "2012-10-17"
   :Statement [{:Sid "PublicReadGetObject"
                :Effect "Allow"
                :Principal "*"
                :Action ["s3:GetObject"]
                :Resource [(str "arn:aws:s3:::" bucket-name "/*")]}]})

(def root-domain "cljs.io")

;; Validation
(defn buckets-available? [cred & buckets]
  ;; (pprint (map #(s3/does-bucket-exist cred %) buckets))
  (not-any? true? (map #(s3/does-bucket-exist cred %) buckets)))

(defn username-available? [cred name]
  (not ((set (map :user-name (:users (iam/list-users cred)))) name)))

;; S3 ===========================================================================

(defn enable-website
  "Enable website hosting for given bucket"
  [cred bucket {:keys [index error redirect]}]
  (s3/set-bucket-website-configuration
   cred
   :bucket-name bucket
   :configuration (cond
                    redirect {:redirect-all-requests-to {:host-name redirect}}
                    index    (merge {:index-document-suffix index}
                                    (if error {:error-document error})))))

(defn website-endpoint
  "Get website endpoint for given bucket"
  [cred bucket]
  (let [region (s3/get-bucket-location cred bucket)]
    (str bucket ".s3-website-" region ".amazonaws.com/")))

(defn create-buckets
  "Create all required S3 buckets"
  [cred root-domain {:keys [logging?]}]
  (let [www-domain (str "www." root-domain)
        log-domain (str "logs." root-domain)]
    (assert (buckets-available? cred root-domain www-domain)
            "Buckets not available")
    (s3/create-bucket cred root-domain)
    (s3/create-bucket cred www-domain)
    (if logging? (s3/create-bucket cred log-domain))
    (enable-static-website-hosting cred root-domain)
    (redirect-bucket-traffic cred www-domain (website-endpoint cred root-domain))))

(defn delete-buckets
  "Delete given buckets"
  [cred root-domain {:keys [logging?]}]
  [cred root-domain {:keys [logging?]}]
  (let [www-domain (str "www." root-domain)
        log-domain (str "logs." root-domain)]
    (s3/delete-bucket cred root-domain)
    (s3/delete-bucket cred www-domain)))
