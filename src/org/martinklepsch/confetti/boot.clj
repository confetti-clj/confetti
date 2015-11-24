(ns org.martinklepsch.confetti.boot
  {:boot/export-tasks true}
  (:require [org.martinklepsch.confetti.report :as rep]
            [org.martinklepsch.confetti.util :as util]
            [org.martinklepsch.confetti.cloudformation :as cf]
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

(b/deftask confetti
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

;; ---

(comment

)
