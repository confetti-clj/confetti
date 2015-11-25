(ns confetti.report
  (:require [confetti.cloudformation :as cf]
            [confetti.util :as util]
            [camel-snake-kebab.core :as case]
            [clojure.pprint :as pp]
            [clojure.data.json :as json]
            [clojure.stacktrace :as strace]))

(def reported (agent #{} :error-handler (fn [a e]
                                          (set-error-mode! a :fail)
                                          (strace/print-stack-trace e))))

(defn mk-reporter
  [{:keys [stack-id report-cb done-promise]}]
  {:pre  [stack-id report-cb done-promise]}
  (fn [reported]
    (let [
          ;;events (get-events-stub stack-id)
          events (sort-by :timestamp (cf/get-events stack-id))
          ]
      ;; (let [r (rand)]
      ;;   (when (> 0.2 r)
      ;;     (println "throwing!" r)
      ;;     (throw (ex-info "Fabricated exception" {}))))
      (doseq [ev events]
        (when-not (reported ev)
          (report-cb ev)))
      (when (cf/succeeded? events)
        (deliver done-promise :success))
      (when (cf/failed? events)
        (deliver done-promise :failure))
      (reduce conj reported events))))

(defn report-stack-events
  [{:keys [stack-id report-cb] :as args}]
  {:pre  [stack-id report-cb]}
  (let [done   (promise)
        report (mk-reporter (assoc args :done-promise done))
        sched  (util/schedule #(send-off reported report) 300)]
    ;; TODO if success? -> error handling!
    (case @done
      :success (future-cancel sched)
      :failure (future-cancel sched))))

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
