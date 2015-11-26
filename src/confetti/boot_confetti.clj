(ns confetti.boot-confetti
  {:boot/export-tasks true}
  (:require [confetti.s3-deploy :as s3d]
            [confetti.report]
            [clojure.string :as string]
            [clojure.pprint :as pp]
            [boot.pod :as pod]
            [boot.util :as u]
            [boot.core :as b]))

(def deps '[[camel-snake-kebab "0.3.2"]
            ;; Should be used in pods
            [confetti/cloudformation "0.1.0-SNAPSHOT"]
            [confetti/s3-deploy "0.1.0-SNAPSHOT"]
            ;; Ahem...
            [com.google.guava/guava "18.0"]])

(defn confetti-pod []
  (pod/make-pod (update-in (b/get-env) [:dependencies] into deps)))

(def cpod (confetti-pod))

;; (let [verbose true]
;;   (pod/with-call-in cpod
;;     (confetti.report/report-stack-events
;;      {:stack-id (:stack-id ran)
;;       :report-cb confetti.util/print-ev})))

(defn print-outputs [cred stack-id]
  (let [outs (pod/with-call-in cpod
               (confetti.cloudformation/get-outputs ~cred stack-id))]
    (doseq [[k o] outs]
      (newline)
      (u/info (:description o))
      (println "->" (:output-value o)))))

(b/deftask create-site
  "Create all resources for ideal deployment of static sites and Single Page Apps.

   The domain your site should be reached under should be passed via the `domain`
   option.

   If you are supplying a root/APEX domain enabling the DNS management via Route53
   is required (more information in the README)."
  [n dns           bool "Handle DNS? (i.e. create Route53 Hosted Zone)"
   c creds K=W     {kw str} "Credentials to use for creating CloudFormation stack"
   v verbose       bool "Print all events in full during creation"
   d domain DOMAIN str  "Domain of the future site (without protocol)"
   r dry-run       bool "Only print to be ran template, don't run it"]
  (b/with-pre-wrap fs
    (assert creds "Credentials are required!")
    (assert domain "Domain is required!")
    (when (pod/with-call-in cpod (confetti.util/root-domain? ~domain))
      (assert dns "Root domain setups must enable `dns` option"))
    (let [tpl (pod/with-call-in cpod
                (confetti.cloudformation/template {:dns? dns}))
          stn (str (string/replace domain #"\." "-") "-confetti-static-site" )
          ran (when-not dry-run
                (pod/with-call-in cpod
                  (confetti.cloudformation/run-template ~creds ~stn ~tpl {:user-domain ~domain})))]
      (if dry-run
        (pp/pprint tpl)
        (do
          (u/info "Reporting stack-creation events for stack:\n")
          (println (:stack-id ran))
          (newline)
          (confetti.report/report-stack-events
           {:stack-id (:stack-id ran)
            :cred creds
            :verbose verbose
            :report-cb confetti.report/cf-report})
          (print-outputs creds (:stack-id ran))
          (when dns
            (newline)
            (u/info "You're using a root domain setup.")
            (println "Make sure your domain is setup to use the nameservers by the Route53 hosted zone.")
            (println "To look up these nameservers go to: ")
            (u/info "https://console.aws.amazon.com/route53/home?region=us-east-1#hosted-zones:")
            (println "In a future release we will print them here directly :)"))))
      fs)))

(defn ^:private fileset->file-map [fs]
  (into {} (for [[p tf] (:tree fs)]
             [p (b/tmp-file tf)])))

(defn serialize-file-map [fm]
  (into {} (for [[p f] fm] [p (.getCanonicalPath f)])))

(b/deftask sync-bucket
  "Sync fileset or directory to S3 bucket."
  [b bucket BUCKET str      "Name of S3 bucket to push files to"
   c creds K=W     {kw str} "Credentials to use for pushing to S3"
   p prefix PREFIX str      "[not implemented] String to strip from paths in fileset/dir"
   d dir DIR       str      "[not implemented] Directory to sync"
   y dry-run       bool     "Report as usual but don't actually do anything"
   _ prune         bool     "Delete files from S3 bucket not in fileset/dir"]
  (b/with-pre-wrap fs
    (assert bucket "A bucket name is required!")
    (assert creds "Credentials are required!")
    (newline)
    (let [file-map (fileset->file-map fs)]
      (confetti.s3-deploy/sync!
       creds bucket file-map
       {:dry-run? dry-run :prune? prune :report-fn confetti.report/s3-report}))
    (newline)
    fs))

;; ---

(comment

)
