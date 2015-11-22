(ns org.martinklepsch.confetti
  (:require [org.martinklepsch.confetti.cloudformation :as cf]
            [org.martinklepsch.confetti.util :as util]
            [camel-snake-kebab.core :as case]
            [clojure.pprint :refer [pprint]]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]))

;; TODO make sure future is retrieved/deref'd at some point to make any exceptions bubble up
;; The stuff below might not be needed anymore if this works properly
(Thread/setDefaultUncaughtExceptionHandler
 (reify Thread$UncaughtExceptionHandler
   (uncaughtException [_ thread ex]
     (println ex "Uncaught exception on" (.getName thread)))))

(defmulti print-event (comp case/->kebab-case-keyword :resource-status))

(defmethod print-event :default [ev]
  (println "DEFAULT EVENT PRINTER")
  (println ev))

(defmethod print-event :create-complete [ev]
  ;; (printf "[%s] %s\n" (:resource-status ev) (:resource-type ev))
  (println "[" (:resource-status ev) "]" (:resource-type ev)))

(defmethod print-event :create-in-progress [ev]
  ;; `printf` behaves strange in non main threads??
  ;; (println (System/currentTimeMillis))
  (println "[" (:resource-status ev) "]" (:resource-type ev)))

(def stack-events (atom {}))

(defn report-events
  ([stack-id done-promise]
   (report-events stack-id done-promise false))
  ([stack-id done-promise verbose?]
   (let [;;events (cf/get-events stack-id)
         events (get-events-stub stack-id)
         new    (reduce
                 (fn [m {:keys [event-id] :as ev}]
                   (if (get m event-id)
                     m
                     (do
                       (print-event ev)
                       (when verbose? (println ev))
                       (assoc m event-id ev))))
                 @stack-events
                 events)]
     (reset! stack-events new)
     (when (cf/succeeded? events)
       (deliver done-promise true)))))

(defn report-stack-events [stack-id opts]
  (let [done  (promise)
        report #(report-events stack-id done true)
        sched  (util/schedule report 300)]
    ;; TODO if success? -> error handling!
    (when @done
      (future-cancel sched))))

(comment
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

  (do
    (reset! evs [])
    (reset! stack-events {})
    (report-stack-events "x" {}))

  (do ;; Events testing
    (def evs (atom []))

    (defn get-events-stub [_]
      (Thread/sleep 300)
      (swap! evs conj
             (if (< (count @evs) 10)
               {:event-id (gensym) :resource-status "CREATE_IN_PROGRESS" :resource-type "AWS::S3::Bucket" :fake-event "yes"}
               {:event-id (gensym) :resource-status "CREATE_COMPLETE" :resource-type "AWS::CloudFormation::Stack"}))))

  (def p   (promise))
  (def ea  (events-reporter "x" p))
  (def fut (util/schedule ea 2))
  (def fut (util/schedule (fn [] (println "x")) 2))

  )
