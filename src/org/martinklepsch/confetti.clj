(ns org.martinklepsch.confetti
  (:require [org.martinklepsch.confetti.cloudfront :as cf]
            [org.martinklepsch.confetti.route53 :as r53]
            [org.martinklepsch.confetti.s3 :as s3]
            ;[amazonica.aws.route53 :as r53]
            [amazonica.aws.identitymanagement :as iam]
            [clojure.pprint   :refer [pprint]]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]))

;; AmazonAWS DNS Servers:
;; DNS1: ns-1769.awsdns-29.co.uk
;; DNS2: ns-267.awsdns-33.com
;; DNS3: ns-993.awsdns-60.net
;; DNS4: ns-1090.awsdns-08.org

;; Validation
(def devcred (read-string (slurp "aws-cred.edn")))

(defn create-project [cred root-domain]
  (log/info "Create bucket...")
  (log/info (pr-str (s3/create-buckets cred root-domain)))
  (log/info "Create HostedZone...")
  (log/info (pr-str (r53/create-hosted-zone cred root-domain)))
  (log/info "Add Record sets to HostedZone...")
  (log/info (pr-str (r53/add-record-sets cred root-domain)))
  (log/info "Create Cloudfront Distribution")
  (log/info (pr-str (cf/create-distribution cred root-domain))))

;; (create-project devcred "cljs.io")

;; (defn delete-project [cred root-domain]
;;   nil)

;; (defn username-available? [cred name]
;;   (not ((set (map :user-name (:users (iam/list-users cred)))) name)))

(comment
  (process-tx cred {:aws/model :x})
  (map #(process-tx cred %) (map reverse-tx (gen-transactions "xxxxxxxxxx.com")))
  (map reverse-tx (gen-transactions "xxxxxxxxxx.com"))

  (create-buckets cred "cljs.io" {})
  (delete-buckets cred "cljs.io" {})
  (s3/does-bucket-exist cred "sdd87dfv8fv7v87ads8s")
  (s3/does-bucket-exist cred "cljs.io")
  (not-any? true? [false false])
  (buckets-available? cred "sdd87dfv8fv7v87ads8s" "mxxxxxxjhdsfsdf98gfdfsdf3")

  (s3/set-bucket-website-configuration
   cred
   :bucket-name "skinnnn"
   :configuration {;:index-document-suffix "index.html"
                                        ;:error-document (or nil "")
                   })
  (s3/get-bucket-website-configuration cred "skinnnn")

  (s3/set-bucket-website-configuration
   cred
   :bucket-name "skinnnn"
   :configuration {:index-document-suffix "index.html"
                                        ;:error-document (or nil "")
                   })
  (s3/get-bucket-website-configuration cred "www.martinklepsch.org") 

  (does-user-exist cred pn)

  (def pn "my-project-1md0l4mb3")
  (s3/create-bucket pn)
  (s3/delete-bucket pn)
  (iam/create-user cred :user-name pn)
  (iam/delete-user cred :user-name pn)
  (iam/put-user-policy :user-name pn
                       :policy-name (str pn "-S3FullAccess")
                       :policy-document (json/write-str (policy pn)))
  (iam/create-access-key :user-name pn)
  (count (map :user-name (:users (iam/list-users cred))))
  (map :name (s3/list-buckets cred)))

