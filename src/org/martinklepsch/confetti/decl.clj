(ns org.martinklepsch.confetti.decl
  (:require [amazonica.aws.s3 :as s3]
            [amazonica.aws.route53 :as r53]
            [amazonica.aws.identitymanagement :as iam]
            [clojure.pprint   :refer [pprint]]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]))

;; Various ways to manage state in declarative style
(comment
  :confetti/state :fresh
  ;:confetti/state :exists
  :confetti/state :not-present)

(defn gen-transactions [root-domain]
  (let [www-domain (str "www." root-domain)]
    [{:aws/model            :aws.s3/bucket
      :confetti/state       :fresh
      :aws.s3/bucket-name   root-domain
      :aws.s3/website       {:index "index.html"
                             :error "error.html"}
      :aws.s3/bucket-policy (json/write-str (bucket-policy root-domain))}
     {:aws/model           :aws.s3/bucket
      :confetti/state      :fresh
      :aws.s3/bucket-name  www-domain
      :aws.s3/website      {:redirect root-domain}}
     {:aws/model                    :aws.route53/hosted-zone
      :aws.route53/hosted-zone-name (str root-domain)}]))


(def schemas
  {:aws.s3/bucket [:aws/model :confetti/state :aws.s3/bucket-name :aws.s3/website-redirect :aws.s3/bucket-policy]})

(defmulti validate-tx (fn [tx] (:aws/model tx)))

(defmethod validate-tx :default [tx]
  (throw (Exception. (str "No validator for model " (:aws/model tx)))))

(defmethod validate-tx :aws.s3/bucket [tx]
  (cond
    (= :fresh (:confetti/state tx))
    (let [bn (:aws.s3/bucket-name tx)]
      [(not (s3/does-bucket-exist bn)) :confetti/bucket-available? bn])
    :else
    [true]))

(defn validate-txs [txs]
  (let [results (map validate-tx txs)]
    (pprint results)
    (not-any? false? (map first results))))

(defmulti process-tx (fn [c tx] (:aws/model tx)))

(defmethod process-tx :default [c tx]
  (throw (Exception. (str "No processor for model " (:aws/model tx)))))

(defmethod process-tx :aws.s3/bucket [c tx]
  (let [bn    (:aws.s3/bucket-name tx)
        state (:confetti/state tx)]
    (when (= state :not-present)
      (log/info "Deleting bucket" bn)
      (s3/delete-bucket c bn))
    (when (= :fresh state)
      (log/info "Creating bucket" bn)
      (s3/create-bucket c bn))
      (if-let [web (:aws.s3/website tx)]
        (do
          (log/info "Use bucket" bn "for static website hosting")
          (enable-website c bn (:aws.s3/website tx)))
        (do
          (log/info "Don't use bucket" bn "for static website hosting")
          (s3/delete-bucket-website-configuration c bn)))
      (if-let [pol (:aws.s3/bucket-policy tx)]
        (do 
          (log/info "Provide policy for bucket" bn)
          (s3/set-bucket-policy c bn pol))
        (do 
          (log/info "Don't provide policy for bucket" bn)
          (s3/delete-bucket-policy c bn)))
      (if-let [target (:aws.s3/website-redirect tx)]
        (do
          (log/info "Redirect traffic for bucket" bn)
          (redirect-bucket-traffic c bn target)))))

(defmulti reverse-tx (fn [tx] (:aws/model tx)))

(defmethod reverse-tx :default [tx]
  (throw (Exception. (str "No reverser for model " (:aws/model tx)))))

(defmethod reverse-tx :aws.s3/bucket [tx]
  (if (#{:not-present} (:confetti/state tx))
    (throw (Exception. "Reversing destructive transactions is not supported yet"))
    (merge (select-keys tx [:aws/model :aws.s3/bucket-name])
           {:confetti/state :not-present})))
