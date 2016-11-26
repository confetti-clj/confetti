(ns confetti.boot-confetti
  {:boot/export-tasks true}
  (:require [confetti.serialize :refer [->str]]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.pprint :as pp]
            [boot.pod :as pod]
            [boot.util :as u]
            [boot.core :as b]))

(def deps '[[camel-snake-kebab "0.3.2"]
            [confetti/cloudformation "0.1.2"]
            [confetti/s3-deploy "0.1.1"]
            [confetti/confetti "0.1.2-SNAPSHOT"] ; for serialize/report ns
            [com.google.guava/guava "18.0"]])

(defn confetti-pod []
  (pod/make-pod (assoc (b/get-env) :dependencies deps)))

(defn prep-pod [cpod]
  (pod/with-eval-in cpod
    (require 'boot.util)
    (require 'confetti.cloudformation)
    (require 'confetti.s3-deploy)
    (require 'confetti.serialize)
    (require 'confetti.report))
  cpod)

(defn assert-exit [assert-success? & msgs]
  (when-not assert-success?
    (u/exit-error
     (doseq [msg msgs]
       (u/fail (str msg "\n\n"))))))

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
  "Create all resources for ideal deployment of static sites and single page apps.

   The domain your site should be reached under should be passed via the `domain`
   option. The `access-key` and `secret-key` options should contain valid AWS creds.

   If you are supplying a root/APEX domain enabling the DNS management via Route53
   is required (more information in the README)."
  [n dns           bool "Handle DNS? (i.e. create Route53 Hosted Zone)"
   a access-key A  str  "AWS access key to use"
   s secret-key S  str  "AWS secret key to use"
   v verbose       bool "Print all events in full during creation"
   d domain DOMAIN str  "Domain of the future site (without protocol)"
   r dry-run       bool "Only print to be ran template, don't run it"]
  (b/with-pre-wrap fs
    (assert-exit domain "The :domain option of the create-site task is required!")
    (assert-exit access-key "The :access-key option of the create-site task is required!")
    (assert-exit secret-key "The :secret-key option of the create-site task is required!")
    (let [cpod (prep-pod (confetti-pod))]
      (when (pod/with-call-in cpod (confetti.util/root-domain? ~domain))
        (assert-exit dns "Root domain setups must enable the :dns option"))
      (let [creds {:access-key access-key :secret-key secret-key}
            tpl (pod/with-eval-in cpod
                  (confetti.cloudformation/template {:dns? ~dns}))
            stn (string/replace domain #"\." "-")
            ran (when-not dry-run
                  (pod/with-call-in cpod
                    (confetti.cloudformation/run-template ~creds ~stn ~tpl {:user-domain ~domain})))
            fname (str stn ".confetti.edn")]
        (if dry-run
          (pp/pprint tpl)
          (do
           (u/info "Reporting events generated while creating your stack.\n")
           (println "Be aware that creation of CloudFront distributions may take up to 15min.")
           (println "In case you connection breaks up or it takes longer you can fetch the stack outputs using")
           (newline)
           (println "    boot fetch-outputs --access-key abc --secret-key xyz")
           (newline)
           (save-outputs (io/file fname) (:stack-id ran) {})
           (pod/with-eval-in cpod
             (confetti.report/report-stack-events
              {:stack-id (:stack-id ~ran)
               :cred ~creds
               :verbose ~verbose
               :report-cb (resolve 'confetti.report/cf-report)}))
           (let [outputs (pod/with-eval-in cpod
                           (confetti.cloudformation/get-outputs ~creds ~(:stack-id ran)))]
             (save-outputs (io/file fname) (:stack-id ran) outputs)
             (newline)
             (print-outputs outputs)
             (newline)
             (u/info "These outputs have also been saved to %s\n" fname))
           (when dns
             (newline)
             (u/info "You're using a root domain setup.\n")
             (println "Make sure your domain is setup to use the nameservers by the Route53 hosted zone.")
             (println "To look up these nameservers go to: ")
             (u/info "https://console.aws.amazon.com/route53/home?region=us-east-1#hosted-zones:\n")
             (println "In a future release we will print them here directly :)"))))
       fs))))

(b/deftask fetch-outputs
  "Download the Cloudformation outputs for all preliminary confetti.edn files in the current directory"
  [a access-key A  str  "AWS access key to use"
   s secret-key S  str  "AWS secret key to use"]
  (b/with-pass-thru _
    (let [cpod        (prep-pod (confetti-pod))
          creds       {:access-key access-key :secret-key secret-key}
          preliminary (->> (System/getProperty "user.dir")
                           clojure.java.io/file
                           (.listFiles)
                           (remove #(.isDirectory %))
                           (filter #(.endsWith (.getName %) ".confetti.edn"))
                           (remove (comp :cloudfront-url edn/read-string slurp)))]
      (doseq [p preliminary]
        (u/info "Fetching outputs for %s\n" (.getName p))
        (let [stack-id (-> p slurp edn/read-string :stack-id)
              outputs  (pod/with-eval-in cpod
                         (try
                           (confetti.cloudformation/get-outputs ~creds ~stack-id)
                           (catch Exception e
                             (boot.util/fail "%s: %s\n" (.getMessage e) (-> e ex-data :stack-info :stack-status))
                             (println (ex-data e)))))]
          (save-outputs p stack-id outputs))))))

(defn ^:private fileset->file-maps [fs]
  (mapv (fn [tf] {:s3-key (:path tf) :file (b/tmp-file tf)})
        (b/output-files fs)))

(defn read-confetti-edn [id]
  (let [f (io/file (str id ".confetti.edn"))]
    (assert-exit (.exists f) (str "The file " (.getName f) " could not be found!"))
    (-> f slurp edn/read-string)))

(b/deftask sync-bucket
  "Sync fileset (default & easiest), directory or selected files to S3 bucket.

   Use the `dir` option to specify a directory to sync. To upload only selected
   files or attach special metadata use `file-maps` or `file-maps-path` options.
   These two options are very similar `file-maps` takes file-maps as EDN data
   whereas `file-maps-path` loads this EDN data from a file in the fileset.

   In both cases the EDN data should be a sequence of maps containing at least an
   `:s3-key` and `:file` key. Optionally these maps may contain a `:metadata` key.
   The `:file` key can be a path pointing to a file in the fileset (no leading /)
   or a path pointing to any other file on your filesystem (with leading /).

   Other options:
   - `confetti-edn` if provided this file is used to specify the bucket, credentials and cloudfront-id options
   - `dry-run` will cause all S3 related side effects to be skipped
   - `prune` will cause S3 objects which are not supplied as file-maps to be
     deleted from the target S3 bucket"
  [e confetti-edn   PATH   str  "The name of a .confetti.edn file in the current working directory"
   b bucket         BUCKET str  "Name of S3 bucket to push files to"
   a access-key     ACCESS str  "AWS access key to use"
   s secret-key     SECRET str  "AWS secret key to use"
   d dir            DIR    str  "Directory to sync as is"
   m file-maps      MAPS   edn  "EDN description of files to upload"
   f file-maps-path PATH   str  "Path to file w/ EDN description of files to upload (file must be in fileset)"
   y dry-run               bool "Report as usual but don't actually do anything"
   p prune                 bool "Delete files from S3 bucket not in fileset/dir"
   c cloudfront-id  DIST   str  "CloudFront Distribution ID to create invalidation (optional)"]
  (b/with-pre-wrap fs
    (let [cedn (read-confetti-edn confetti-edn)]
      ;; Various checking / error handling
      (if confetti-edn
        (do
          (assert-exit (:bucket-name cedn) "You supplied the confetti-edn option but it does not contain a :bucket-name key")
          (assert-exit (:secret-key cedn) "You supplied the confetti-edn option but it does not contain a :access-key key")
          (assert-exit (:access-key cedn) "You supplied the confetti-edn option but it does not contain a :secret-key key")
          (assert-exit (and (not bucket) (not cloudfront-id) (not access-key) (not secret-key))
                  "When supplying the confetti-edn option, don't provide the bucket, cloudfront-id, access-key or secret-key options"))
        (do
          (assert-exit bucket "Bucket option is required!")
          (assert-exit access-key "Access Key option is required!")
          (assert-exit secret-key "Secret Key option is required!")))
      (newline)

      (let [bucket (if cedn (:bucket-name cedn) bucket)
            cf-id  (if cedn (:cloudfront-id cedn) cloudfront-id)
            creds  (if cedn
                     (select-keys cedn [:access-key :secret-key])
                     {:access-key access-key :secret-key secret-key})
            ;; Create pod and require needed namespaces
            cpod   (prep-pod (confetti-pod))
            ;; Read file-maps from the various possible sources
            fmaps  (cond
                     file-maps      file-maps
                     file-maps-path (edn/read-string (slurp (b/tmp-file (get-in fs [:tree file-maps-path]))))
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
                       ~creds ~bucket (confetti.serialize/->file ~(pod/send! (->str fmaps*)))
                       {:dry-run? ~dry-run :prune? ~prune :report-fn (resolve 'confetti.report/s3-report)}))]
        (let [{:keys [uploaded updated unchanged deleted]} results]
          (when (< 0 (max (count uploaded) (count updated) (count deleted)))
            (newline))
          (let [paths (mapv #(str "/" %) (concat uploaded updated deleted))]
            (when (and cf-id (seq paths) (not dry-run))
              (u/info "Creating CloudFront invalidation for %s paths.\n" (count paths))
              (u/dbug "Paths: %s\n" (pr-str paths))
              (pod/with-eval-in cpod
                (confetti.s3-deploy/cloudfront-invalidate! ~creds ~cf-id ~paths)))))
        (u/info "%s new files uploaded.\n" (-> results :uploaded count))
        (u/info "%s existing files updated. %s unchanged.\n"
                (-> results :updated count)
                (-> results :unchanged count))
        (u/info "%s files deleted.\n" (-> results :deleted count))))
    fs))