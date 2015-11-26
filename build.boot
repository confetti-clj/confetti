(set-env!
 :source-paths   #{"src"}
 :dependencies '[[adzerk/bootlaces "0.1.11" :scope "test"]
                 [camel-snake-kebab "0.3.2"]
                 ;; Should be used in pods
                 [confetti/cloudformation "0.1.0-SNAPSHOT"]
                 [confetti/s3-deploy "0.1.0-SNAPSHOT"]
                 ;; Ahem...
                 [com.google.guava/guava "18.0"]])

(require '[adzerk.bootlaces :refer [bootlaces! build-jar push-snapshot push-release]]
         '[confetti.boot-confetti :refer [create-site sync-bucket]])

(def +version+ "0.1.0-SNAPSHOT")
(bootlaces! +version+)

(task-options!
 push {:ensure-clean false}
 pom {:project     'confetti/confetti
      :version     +version+
      :description "Create AWS resources for static site and single page app deployments"
      :url         "https://github.com/confetti/confetti"
      :scm         {:url "https://github.com/confetti/confetti"}})
