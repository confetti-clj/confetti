(ns org.martinklepsch.confetti.util
  (:import [java.util.concurrent Executors TimeUnit]))

(def scheduler (Executors/newScheduledThreadPool 1))

(defn schedule [fn interval-seconds]
  (.scheduleAtFixedRate scheduler fn 0 interval-seconds TimeUnit/MILLISECONDS))

(comment
  (def fut (schedule (fn [] (println (System/currentTimeMillis))) 3))
  ;; Test exception handling
  (def fx (let [s (atom 0)
                f (fn [] (let [n (inc @s)]
                           (println n)
                           (reset! s n)
                           (when (> n 5)
                             (throw (ex-info "Failure in ScheduledFuture" {:n n})))))
                fut (schedule f 1)]
            fut))

  (future-cancel fx)

  (deref fx)

  )
