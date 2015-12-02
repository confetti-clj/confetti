(set-env! :dependencies '[[confetti/confetti "0.1.0-SNAPSHOT" :scope "test"]
                          [org.martinklepsch/boot-gzip "0.1.2" :scope "test"]])

(require '[confetti.boot-confetti :refer [sync-bucket]]
         '[org.martinklepsch.boot-gzip :refer [gzip]]
         '[clojure.java.io :as io]
         '[clojure.string :as string])

;; Creating and compressing files ----------------------------------------------

(def segments
  ["hello" "bye" "foo" "bar" "baz" "word" "simple" "storage" "service"
   "word" "gibberish" "clojure" "compression" "amazon" "gzip"])

(defn generate-random-string [size]
  (string/join " " (shuffle (flatten (take size (repeat segments))))))

(deftask create-files
  "Let's create some random files for uploading"
  [n number NUM int "Number of files to create"]
  (with-pre-wrap fs
    (let [tmp (tmp-dir!)]
      (doseq [i (range number)
              :let [f (io/file tmp "nested" (str i ".txt"))]]
        (io/make-parents f)
        (spit f (generate-random-string 100)))
      (-> fs (add-resource tmp) commit!))))

;; Actual deployment stuff -----------------------------------------------------
;; This is a non-trivial example, in many cases just syncing the fileset
;; is enough. This example is non-trivial for the following reasons:
;;
;; - files are gzipped and uploaded w/ custom content-encoding metadata
;; - files are uploaded to specific locations depending on whether
;;   they're gzipped or not

(def aws (read-string (slurp "aws.edn")))

(def file-maps-file "file-maps.edn")

(defn ->s3-key [fileset-path gzip?]
  (let [p (string/replace fileset-path #"\.gz$" "")]
    (str (when gzip? "compressed/") p)))

(defn fileset->file-maps [fileset]
  (for [[_ tmpf] (:tree fileset)
        :let [gzip? (.endsWith (:path tmpf) ".gz")]]
    {:s3-key   (->s3-key (:path tmpf) gzip?)
     :file     (.getCanonicalPath (tmp-file tmpf))
     :metadata (when gzip? {:content-encoding "gzip"})}))

(deftask save-file-maps []
  (with-pre-wrap fs
    (let [tmp (tmp-dir!)]
      (spit (io/file tmp file-maps-file)
            (pr-str (fileset->file-maps fs)))
      (-> fs (add-resource tmp) commit!))))

(deftask deploy []
  (comp (create-files :number 5)
        (gzip :regex [#"nested/*"])
        (save-file-maps)
        (sync-bucket :fmap    file-maps-file
                     :bucket  (:bucket aws)
                     :creds   (select-keys aws [:access-key :secret-key]))))
