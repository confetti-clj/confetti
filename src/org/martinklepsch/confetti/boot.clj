(ns org.martinklepsch.confetti.boot
  (:require [org.martinklepsch.confetti :as confetti]
            [org.martinklepsch.confetti.cloudformation :as cf]
            [clojure.pprint :as pp]
            [boot.core :as b]))

(b/deftask confetti
  "Create all resources for ideal deployment of static sites"
  [n dns?          boolean "Handle DNS? (i.e. create Route53 Hosted Zone)"
   d domain DOMAIN str     "Domain of the future site (without protocol)"
   r dry-run?      boolean "Only print to be ran template, don't run it"]
  (c/with-pre-wrap fs
    (assert domain "Domain is required!")
    (let [tpl (cf/template {:dns? dns?})
          stn (str "confetti-static-site" domain)
          ran (when-not dry-run?
                (cf/run-template stn tpl {:user-domain domain}))]
      (if dry-run?
        (pp/pprint tpl)
        (do
          (println ran)
          (confetti/report-stack-events (:stack-id ran))))
      fs)))
