(ns org.martinklepsch.confetti
  (:require [amazonica.aws.s3 :as s3]
            [amazonica.aws.cloudfront :as cf]
            [amazonica.aws.route53 :as r53]
            [amazonica.aws.identitymanagement :as iam]
            [clojure.pprint   :refer [pprint]]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]))

(comment
  (process-tx cred {:aws/model :x})
  (map #(process-tx cred %) (map reverse-tx (gen-transactions "xxxxxxxxxx.com")))
  (map reverse-tx (gen-transactions "xxxxxxxxxx.com"))


  ;; (create-buckets cred "cljs.io" {})
  ;; (delete-buckets cred "cljs.io" {})
  ;; (s3/does-bucket-exist cred "sdd87dfv8fv7v87ads8s")
  ;; (s3/does-bucket-exist cred "cljs.io")
  ;; (not-any? true? [false false])
  ;; (buckets-available? cred "sdd87dfv8fv7v87ads8s" "mxxxxxxjhdsfsdf98gfdfsdf3")

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
  (iam/create-access-key :user-name pn))
;; (count (map :user-name (:users (iam/list-users cred))))
;; (map :name (s3/list-buckets cred))

