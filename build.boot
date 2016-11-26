(set-env!
 :source-paths   #{"src"}
 :dependencies '[[adzerk/bootlaces "0.1.13" :scope "test"]
                 [amazonica/amazonica "0.3.33" :scope "test"]
                 [com.google.guava/guava "18.0" :scope "test"]])

(require '[adzerk.bootlaces :refer [bootlaces! build-jar push-snapshot push-release]]
         '[confetti.boot-confetti :refer [create-site report-progress fetch-outputs sync-bucket]])

(def +version+ "0.1.4")
(bootlaces! +version+)

(def creds
  (try
    (read-string (slurp "aws-cred.edn"))
    (catch java.io.FileNotFoundException e
      (boot.util/warn "aws-cred.edn not found, no authentication will be performed\n")
      "nil")))

(task-options!
 sync-bucket {:secret-key (:secret-key creds)
              :access-key (:access-key creds)}
 fetch-outputs {:secret-key (:secret-key creds)
                :access-key (:access-key creds)}
 create-site {:secret-key (:secret-key creds)
              :access-key (:access-key creds)}
 push {:ensure-clean false}
 pom {:project     'confetti/confetti
      :version     +version+
      :description "Create AWS resources for static site and single page app deployments"
      :url         "https://github.com/confetti-clj/confetti"
      :scm         {:url "https://github.com/confetti-clj/confetti"}})
