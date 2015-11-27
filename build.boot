(set-env!
 :source-paths   #{"src"}
 :dependencies '[[adzerk/bootlaces "0.1.11" :scope "test"]])

(require '[adzerk.bootlaces :refer [bootlaces! build-jar push-snapshot push-release]]
         '[confetti.boot-confetti :refer [create-site sync-bucket]])

(def +version+ "0.1.0-SNAPSHOT")
(bootlaces! +version+)

(def creds (read-string (slurp "aws-cred.edn")))

(task-options!
 sync-bucket {:creds creds}
 push {:ensure-clean false}
 pom {:project     'confetti/confetti
      :version     +version+
      :description "Create AWS resources for static site and single page app deployments"
      :url         "https://github.com/confetti/confetti"
      :scm         {:url "https://github.com/confetti/confetti"}})
