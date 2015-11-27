(ns confetti.boot-confetti
  {:boot/export-tasks true}
  (:require [confetti.serialize :refer [->str]]
            [clojure.string :as string]
            [clojure.pprint :as pp]
            [boot.pod :as pod]
            [boot.util :as u]
            [boot.core :as b]))

(def deps '[[camel-snake-kebab "0.3.2"]
            [confetti/cloudformation "0.1.0-SNAPSHOT"]
            [confetti/s3-deploy "0.1.0-SNAPSHOT"]
            [com.google.guava/guava "18.0"]])

(defn confetti-pod []
  (pod/make-pod (update-in (b/get-env) [:dependencies] into deps)))

(defn prep-pod [cpod]
  (pod/with-eval-in cpod
    (require 'confetti.cloudformation)
    (require 'confetti.s3-deploy)
    (require 'confetti.serialize)
    (require 'confetti.report))
  cpod)

;; (let [verbose true]
;;   (pod/with-call-in cpod
;;     (confetti.report/report-stack-events
;;      {:stack-id (:stack-id ran)
;;       :report-cb confetti.util/print-ev})))

(defn print-outputs [outs]
  (doseq [[k o] outs]
    (newline)
    (u/info "%s\n" (:description o))
    (println "->" (:output-value o))))

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
    (let [cpod (prep-pod (confetti-pod))]
      (when (pod/with-call-in cpod (confetti.util/root-domain? ~domain))
        (assert dns "Root domain setups must enable `dns` option"))
      (let [tpl (pod/with-eval-in cpod
                  (confetti.cloudformation/template {:dns? ~dns}))
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
          (pod/with-eval-in cpod
            (confetti.report/report-stack-events
             {:stack-id (:stack-id ~ran)
              :cred ~creds
              :verbose ~verbose
              :report-cb (resolve 'confetti.report/cf-report)}))
          (print-outputs
           (pod/with-eval-in cpod
             (confetti.cloudformation/get-outputs ~creds ~(:stack-id ran))))
          (when dns
            (newline)
            (u/info "You're using a root domain setup.")
            (println "Make sure your domain is setup to use the nameservers by the Route53 hosted zone.")
            (println "To look up these nameservers go to: ")
            (u/info "https://console.aws.amazon.com/route53/home?region=us-east-1#hosted-zones:")
            (println "In a future release we will print them here directly :)"))))
      fs))))

(defn ^:private fileset->file-maps [fs]
  (mapv (fn [tf] {:s3-key (:path tf) :file (b/tmp-file tf)})
        (b/output-files fs)))

(b/deftask sync-bucket
  "Sync fileset (default) or directory to S3 bucket.
   Alternatively supply `fmap` describing to-be-uploaded resources."
  [b bucket BUCKET str      "Name of S3 bucket to push files to"
   c creds K=V     {kw str} "Credentials to use for pushing to S3"
   f fmap PATH     str      "Path to edn file in fileset describing file-map"
   d dir DIR       str      "Directory to sync"
   y dry-run       bool     "Report as usual but don't actually do anything"
   _ prune         bool     "Delete files from S3 bucket not in fileset/dir"]
  (b/with-pre-wrap fs
    (assert bucket "A bucket name is required!")
    (assert creds "Credentials are required!")
    (let [cpod     (prep-pod (confetti-pod))
          file-map (cond
                     fmap  (read-string (slurp (b/tmp-file (get-in fs [:tree fmap]))))
                     dir   (pod/with-eval-in cpod
                             (confetti.s3-deploy/dir->file-maps (clojure.java.io/file ~dir)))
                     :else (fileset->file-maps fs))
          results (pod/with-eval-in cpod
                    (confetti.s3-deploy/sync!
                     ~creds ~bucket (confetti.serialize/->file ~(->str file-map))
                     {:dry-run? ~dry-run :prune? ~prune :report-fn (resolve 'confetti.report/s3-report)}))]
      (newline)
      (u/info "%s new files uploaded.\n" (-> results :uploaded count))
      (u/info "%s existing files updated.\n" (-> results :updated count))
      (u/info "%s files deleted.\n" (-> results :deleted count)))
    fs))

;; ---

(comment
  )
