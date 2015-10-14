(ns org.martinklepsch.confetti.route53
  (:require [amazonica.aws.route53 :as r53]
            [clojure.pprint   :refer [pprint]]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]))

;; Route 53 ====================================================================

(defn website-endpoint->zone-id [ep]
  ;; As per http://docs.aws.amazon.com/general/latest/gr/rande.html#s3_region
  (-> {"s3-website-us-east-1.amazonaws.com"      "Z3AQBSTGFYJSTF"
       "s3-website-us-west-2.amazonaws.com"      "Z3BJ6K6RIION7M"
       "s3-website-us-west-1.amazonaws.com"      "Z2F56UZL2M1ACD"
       "s3-website-eu-west-1.amazonaws.com"      "Z1BKCTXD74EZPE"
       "s3-website.eu-central-1.amazonaws.com"   "Z21DNDUVLTQW6Q"
       "s3-website-ap-southeast-1.amazonaws.com" "Z3O0J2DXBE1FTB"
       "s3-website-ap-southeast-2.amazonaws.com" "Z1WCIGYICN2BYD"
       "s3-website-ap-northeast-1.amazonaws.com" "Z2M4EHUR26P7ZW"
       "s3-website-sa-east-1.amazonaws.com"      "Z7KQH4QJS55SO"
       "s3-website-us-gov-west-1.amazonaws.com"  "Z31GFT0UA1I2HV"}
      (get ep)))

(defn create-hosted-zone [cred root-domain]
  (r53/create-hosted-zone cred
                          :name root-domain
                          :caller-reference (str (java.util.UUID/randomUUID))))

(defn alias-record-set [name endpoint]
  {:name name
   :type "A"
   :alias true
   :alias-target {:hosted-zone-id (website-endpoint->zone-id endpoint)
                  :d-n-s-name endpoint
                  :evaluate-target-health false}})

(defn add-record-sets [cred root-domain]
  (let [zone-name (str root-domain ".")
        endpoint "s3-website-us-east-1.amazonaws.com" ; Should be dynamic really
        zone-id (-> #(= (:name %) zone-name)
                    (filter (:hosted-zones (r53/list-hosted-zones cred)))
                    first :id)]
    (r53/change-resource-record-sets
     cred
     :hosted-zone-id zone-id
     :change-batch
     {:changes [{:action "CREATE"
                 :resource-record-set (alias-record-set zone-name endpoint)}
                {:action "CREATE"
                 :resource-record-set (alias-record-set (str "www." zone-name) endpoint)}]})))

;; (add-record-sets cred "martinklepsch.com")
;; (count (:resource-record-sets (r53/list-resource-record-sets :hosted-zone-id "Z1S0244G792WMB")))
;; (r53/change-resource-record-sets
;;  :hosted-zone-id "Z1S0244G792WMB"
;;  :change-batch {:changes [{:action "CREATE"
;;                            :resource-record-set {:name "martinklepsch.com."
;;                                                  :type "A"
;;                                                  :alias true
;;                                                  :alias-target {:hosted-zone-id "Z3AQBSTGFYJSTF"
;;                                                                 :d-n-s-name "s3-website-us-east-1.amazonaws.com"
;;                                                                 :evaluate-target-health false}}}
;;                           {:action "CREATE"
;;                            :resource-record-set {:name "www.martinklepsch.com."
;;                                                  :type "A"
;;                                                  :alias true
;;                                                  :alias-target {:hosted-zone-id "Z3AQBSTGFYJSTF"
;;                                                                 :d-n-s-name "s3-website-us-east-1.amazonaws.com"
;;                                                                 :evaluate-target-health false}}}]})
