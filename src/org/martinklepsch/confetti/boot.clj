(ns org.martinklepsch.confetti.boot
  (:require [org.martinklepsch.confetti :as confetti]
            [org.martinklepsch.confetti.cloudformation :as cf]
            [boot.core :as b]))

(b/deftask confetti
  "Create all resources for ideal deployment of static sites"
  [n dns?          boolean "Handle DNS? (i.e. create Route53 Hosted Zone)"
   d domain DOMAIN str     "domain of the future site (without protocol)"]
  (c/with-pre-wrap fs
    (assert domain "Domain is required!")
    (let [ran (cf/run-template
               (str "static-site-" domain)
               (cf/template {:dns? dns?})
               {:user-domain domain})]
      (println ran)
      (confetti/report-stack-events (:stack-id ran))
      fs)))
