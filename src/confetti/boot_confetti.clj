(ns confetti.boot-confetti
  {:boot/export-tasks true}
  (:require [confetti.report :as rep]
            [confetti.util :as util]
            [confetti.cloudformation :as cf]
            [confetti.s3-deploy :as s3d]
            [camel-snake-kebab.core :as case]
            [clojure.string :as string]
            [clojure.pprint :as pp]
            [boot.from.io.aviso.ansi :as ansi]
            [boot.util :as u]
            [boot.core :as b]))

(def colors {:create-complete ansi/green
             :create-in-progress ansi/yellow
             :create-failed ansi/red
             :rollback-in-progress ansi/yellow
             :delete-complete ansi/green
             :delete-in-progress ansi/yellow
             :rollback-complete ansi/green})

(defn print-ev [ev]
  (let [type  (-> ev :resource-status case/->kebab-case-keyword)
        color (get colors type identity)]
    (println " -" (color (str "[" (name type) "]")) (:resource-type ev)
             (if-let [r (:resource-status-reason ev)] (str "- " r) ""))))

(defn print-outputs [stack-id]
  (let [outs (cf/get-outputs stack-id)]
    (doseq [[k o] outs]
      (newline)
      (println (ansi/bold (:description o)))
      (println "->" (:output-value o)))))

(b/deftask create-site
  "Create all resources for ideal deployment of static sites and Single Page Apps.

   The domain your site should be reached under should be passed via the `domain`
   option.

   If you are supplying a root/APEX domain enabling the DNS management via Route53
   is required (more information in the README)."
  [n dns           bool "Handle DNS? (i.e. create Route53 Hosted Zone)"
   v verbose       bool "Print all events in full during creation"
   d domain DOMAIN str  "Domain of the future site (without protocol)"
   r dry-run       bool "Only print to be ran template, don't run it"]
  (b/with-pre-wrap fs
    (assert domain "Domain is required!")
    (when (util/root-domain? "hi.abc.com")
      (assert dns "Root domain setups must use Route53 for DNS"))
    (let [tpl (cf/template {:dns? dns})
          stn (str (string/replace domain #"\." "-") "-confetti-static-site" )
          ran (when-not dry-run
                (cf/run-template stn tpl {:user-domain domain}))]
      (if dry-run
        (pp/pprint tpl)
        (do
          (println (ansi/bold "Reporting stack-creation events for stack:"))
          (println (:stack-id ran))
          (newline)
          (rep/report-stack-events
           {:stack-id (:stack-id ran)
            :report-cb #(do (print-ev %)
                            (if verbose (pp/pprint %)))})
          (print-outputs (:stack-id ran))))
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
   p prefix PREFIX str      "String to strip from paths in fileset/dir"
   d dir DIR       str      "Directory to sync"
   y dry-run       bool     "Call report-fn as usual but don't actually do anything"
   _ prune         bool     "Delete files from S3 bucket not in fileset/dir"]
  (b/with-pre-wrap fs
    (assert bucket "a bucket name is required!")
    (assert creds "credentials are required!")
    (let [file-map (fileset->file-map fs)]
      (s3d/sync! bucket file-map {:dry-run? dry-run
                                  :prune? true
                                  :report-fn (fn [t d] (println t (:s3-key d)))})
      #_(pp/pprint (take 3 (serialize-file-map file-map))))
    fs))

;; ---

(comment

)
