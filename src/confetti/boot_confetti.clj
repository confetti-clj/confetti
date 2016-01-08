(ns confetti.boot-confetti
  {:boot/export-tasks true}
  (:require [confetti.serialize :refer [->str]]
            [clojure.string :as string]
            [clojure.java.io :as io]
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
    (u/info "%s\n" (:description o))
    (println "->" (:output-value o))))

(defn save-outputs [file stack-id outputs]
  (->> (for [[k o] outputs]
         [k (:output-value o)])
       (into {:stack-id stack-id})
       pp/pprint
       with-out-str
       (spit file)))

(b/deftask create-site
  "Create all resources for ideal deployment of static sites and Single Page Apps.

   The domain your site should be reached under should be passed via the `domain`
   option.

   If you are supplying a root/APEX domain enabling the DNS management via Route53
   is required (more information in the README)."
  [n dns           bool "Handle DNS? (i.e. create Route53 Hosted Zone)"
   a access-key A  str  "AWS access key to use"
   s secret-key S  str  "AWS secret key to use"
   v verbose       bool "Print all events in full during creation"
   d domain DOMAIN str  "Domain of the future site (without protocol)"
   r dry-run       bool "Only print to be ran template, don't run it"]
  (b/with-pre-wrap fs
    (assert domain "Domain is required!")
    (assert access-key "Access Key is required!")
    (assert secret-key "Secret Key is required!")
    (let [cpod (prep-pod (confetti-pod))]
      (when (pod/with-call-in cpod (confetti.util/root-domain? ~domain))
        (assert dns "Root domain setups must enable `dns` option"))
      (let [creds {:access-key access-key :secret-key secret-key}
            tpl (pod/with-eval-in cpod
                  (confetti.cloudformation/template {:dns? ~dns}))
            stn (string/replace domain #"\." "-")
            ran (when-not dry-run
                  (pod/with-call-in cpod
                    (confetti.cloudformation/run-template ~creds ~stn ~tpl {:user-domain ~domain})))]
      (if dry-run
        (pp/pprint tpl)
        (do
          (u/info "Reporting events generated while creating your stack.\n")
          (println "Be aware that creation of CloudFront distributions may take up to 15min.")
          (newline)
          (pod/with-eval-in cpod
            (confetti.report/report-stack-events
             {:stack-id (:stack-id ~ran)
              :cred ~creds
              :verbose ~verbose
              :report-cb (resolve 'confetti.report/cf-report)}))
          (let [fname (str stn ".confetti.edn")
                outputs (pod/with-eval-in cpod
                          (confetti.cloudformation/get-outputs ~creds ~(:stack-id ran)))]
            (save-outputs (io/file fname) (:stack-id ran) outputs)
            (newline)
            (print-outputs outputs)
            (newline)
            (u/info "These outputs have also been saved to %s\n" fname))
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
  "Sync fileset (default), directory or selected files to S3 bucket.

   Use the `dir` option to specify a directory to sync. To upload only selected
   files or attach special metadata use `file-maps` or `file-maps-path` options.
   These two options are very similar `file-maps` takes file-maps as EDN data
   whereas `file-maps-path` loads this EDN data from a file in the fileset.

   In both cases the EDN data should be a sequence of maps containing at least an
   `:s3-key` and `:file` key. Optionally these maps may contain a `:metadata` key.
   The `:file` key can be a path pointing to a file in the fileset (no leading /)
   or a path pointing to any other file on your filesystem (with leading /).

   Other options:
   - `dry-run` will cause all S3 related side effects to be skipped
   - `prune` will cause S3 objects which are not supplied as file-maps to be
     deleted from the target S3 bucket"
  [b bucket         BUCKET str  "Name of S3 bucket to push files to"
   a access-key     ACCESS str  "AWS access key to use"
   s secret-key     SECRET str  "AWS secret key to use"
   d dir            DIR    str  "Directory to sync as is"
   m file-maps      MAPS   edn  "EDN description of files to upload"
   f file-maps-path PATH   str  "Path to file w/ EDN description of files to upload (file must be in fileset)"
   y dry-run               bool "Report as usual but don't actually do anything"
   p prune                 bool "Delete files from S3 bucket not in fileset/dir"]
  (b/with-pre-wrap fs
    (assert bucket "A bucket name is required!")
    (assert access-key "Access Key is required!")
    (assert secret-key "Secret Key is required!")
    (newline)
    (let [creds {:access-key access-key :secret-key secret-key}
          cpod  (prep-pod (confetti-pod))
          ;; Read file-maps from the various possible sources
          fmaps (cond
                  file-maps      file-maps
                  file-maps-path (read-string (slurp (b/tmp-file (get-in fs [:tree file-maps-path]))))
                  dir            (pod/with-eval-in cpod
                                   (-> (clojure.java.io/file ~dir)
                                       confetti.s3-deploy/dir->file-maps
                                       confetti.serialize/->str))
                  :else          (fileset->file-maps fs))
          ;; If relative paths are supplied lookup files in fileset
          fmaps* (mapv (fn [{:keys [file] :as fm}]
                         (if (and (string? file) (not (.startsWith file "/")))
                           (assoc fm :file (b/tmp-file (get-in fs [:tree file])))
                           fm))
                       fmaps)
          results (pod/with-eval-in cpod
                    (confetti.s3-deploy/sync!
                     ~creds ~bucket (confetti.serialize/->file ~(->str fmaps*))
                     {:dry-run? ~dry-run :prune? ~prune :report-fn (resolve 'confetti.report/s3-report)}))]
      (let [{:keys [uploaded updated unchanged deleted]} results]
        (if (< 0 (max (count uploaded) (count updated) (count deleted)))
          (newline)))
      (u/dbug results)
      (u/info "%s new files uploaded.\n" (-> results :uploaded count))
      (u/info "%s existing files updated. %s unchanged.\n"
              (-> results :updated count)
              (-> results :unchanged count))
      (u/info "%s files deleted.\n" (-> results :deleted count)))
    fs))
