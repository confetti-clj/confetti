(ns confetti.util
  (:require [clojure.string :as string])
  (:import [java.util.concurrent Executors TimeUnit]
           [com.google.common.net InternetDomainName]))

;; Scheduling used for reporting -----------------------------------------------

(def scheduler (Executors/newScheduledThreadPool 1))

(defn schedule [fn interval-ms]
  (.scheduleAtFixedRate scheduler fn 0 interval-ms TimeUnit/MILLISECONDS))

;; Root domain identification --------------------------------------------------

(defn root-domain? [domain]
  (let [suffix (.. (InternetDomainName/from domain) publicSuffix toString)
        ptn    (re-pattern (str "\\." suffix "$"))
        wo-tld (string/replace domain ptn "")]
    (= 0 (count (filter #(= \. %) wo-tld)))))

(comment
   (root-domain? "abc.co.uk")
   (root-domain? "hi.abc.co.uk")
   (root-domain? "bac.com")

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
