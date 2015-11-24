(set-env!
 :source-paths   #{"src"}
 :dependencies '[[adzerk/bootlaces "0.1.11" :scope "test"]
                 [amazonica/amazonica "0.3.33"]
                 [camel-snake-kebab "0.3.2"]
                 [org.clojure/data.json "0.2.6"]
                 ;; Ahem...
                 [com.google.guava/guava "18.0"]])
                 

(require '[adzerk.bootlaces :refer [bootlaces! build-jar]]
         '[org.martinklepsch.confetti.boot :refer [confetti]])

(def +version+ "0.1.0-SNAPSHOT")
(bootlaces! +version+)

(task-options!
 pom {:project     'org.martinklepsch/confetti
      :version     +version+
      :description "Manage S3 Buckets, Cloudfront distributions and whatnot"})
