(ns confetti.report
  (:require [boot.from.io.aviso.ansi :as ansi]
            [camel-snake-kebab.core :as case]
            [confetti.cloudformation :as cf]
            [confetti.util :as util]
            [clojure.pprint :as pp]
            [clojure.stacktrace :as strace]))

;; Reporting S3 ----------------------------------------------------------------

(def s3-colors {:confetti.s3-deploy/upload ansi/green
                :confetti.s3-deploy/update ansi/yellow
                :confetti.s3-deploy/delete ansi/red})

(defn s3-report [{:keys [op s3-key] :as ev}]
  (let [color (get s3-colors op)]
    (println " -" (color (str "[" (name op) "]")) s3-key)))


;; Reporting Cloudformation ----------------------------------------------------

(def reported
  (agent #{}
         :error-handler (fn [a e]
                          (println "There was a problem fetch Stack events from AWS. (" (class e) ")")
                          (println "If it persists, you may cancel this process and use `fetch-outputs` later")
                          #_(if (instance? com.amazonaws.AmazonClientException e)
                            (println "There was a problem communicating with AWS.")
                            (strace/print-stack-trace e)))))

(defn mk-reporter
  [{:keys [cred stack-id report-cb done-promise]}]
  {:pre  [cred stack-id report-cb done-promise]}
  (fn [reported]
    (let [events (sort-by :timestamp (cf/get-events cred stack-id))]
      (doseq [ev events]
        (when-not (reported ev)
          (report-cb ev)))
      (when (cf/succeeded? events)
        (deliver done-promise :success))
      (when (cf/failed? events)
        (deliver done-promise :failure))
      (reduce conj reported events))))

(defn cancel-notice []
  (newline)
  (println "Reporting Stack events takes longer than it should, cancelling.")
  (println "You can always retrieve stack outputs using fetch-outputs."))

(defn report-stack-events
  [{:keys [cred stack-id report-cb] :as args}]
  {:pre  [cred stack-id report-cb]}
  (let [done   (promise)
        report (mk-reporter (assoc args :done-promise done))
        sched  (util/schedule #(send-off reported report) 1000)]

    ;; Sometimes this gets stuck for whatever reason, give it a hard limit:
    (util/after-ms #(deliver done :timeout) (* 16 #_min 60 1000))

    ;; TODO if success? -> error handling!
    (case @done
      :success (future-cancel sched)
      :timeout (do (cancel-notice)
                   (future-cancel sched))
      :failure (future-cancel sched))))

(def cf-colors
  {:create-complete ansi/green
   :create-in-progress ansi/yellow
   :create-failed ansi/red
   :rollback-in-progress ansi/yellow
   :delete-complete ansi/green
   :delete-in-progress ansi/yellow
   :rollback-complete ansi/green})

(defn cf-report [ev]
  (let [type  (-> ev :resource-status case/->kebab-case-keyword)
        color (get cf-colors type identity)]
    (println " -" (color (str "[" (name type) "]")) (:resource-type ev)
             (if-let [r (:resource-status-reason ev)] (str "- " r) ""))))

(comment
  (do
    (reset! evs [])
    (if (agent-error reported)
      (restart-agent reported #{})
      (send reported (fn [_] #{})))
    (future (loop [i 0]
              (Thread/sleep 300)
              (add-event evs)
              (when (< i 10)
                (recur (inc i)))))
    (report-stack-events {:stack-id "x"
                          :report-cb pp/pprint}))

  (do ;; Events testing
    (def evs (atom []))

    (defn get-events-stub [_]
      (deref evs))

      ;; (println "get-events-stub")
    (defn add-event [evs-atom]
      (println "adding event")
      ;; (when (< 0.3 (rand))
      ;;   (println throwing)
      ;;   (throw (ex-info "Fabricated exception" {})))
      (swap! evs-atom conj
             (if (< (count @evs-atom) 10)
               {:event-id (gensym) :resource-status "CREATE_IN_PROGRESS" :resource-type "AWS::S3::Bucket" :fake-event "yes"}
               {:event-id (gensym) :resource-status "CREATE_COMPLETE" :resource-type "AWS::CloudFormation::Stack"}))))

  (def p   (promise))
  (def fut (util/schedule #(send-off reported report) 2000))
  ;; (def fut (util/schedule (fn [] (println "x")) 2))

  (defn rand-str []
    (->> (fn [] (rand-nth ["a" "b" "c" "d" "e" "f" "g" "h" "i" "j" "k"]))
         repeatedly
         (take 5)
         (apply str)))

  (let [s   (rand-str)
        ran (cf/run-template
             (str "static-site-" s)
             (cf/template {:dns? false})
             {:user-domain (str s ".martinklepsch.org")})]
    (println ran)
    (report-stack-events (:stack-id ran)))

  )
