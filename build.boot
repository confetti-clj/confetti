(set-env!
 :source-paths   #{"src"}
 :dependencies '[[adzerk/bootlaces "0.1.11" :scope "test"]
                 [amazonica/amazonica "0.3.33"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/tools.logging "0.3.1"]])
                 

(require '[adzerk.bootlaces :refer [bootlaces! build-jar]])

(def +version+ "0.1.0-SNAPSHOT")
(bootlaces! +version+)

(task-options!
 pom {:project     'org.martinklepsch/confetti
      :version     +version+
      :description "Manage S3 Buckets, Cloudfront distributions and whatnot"})
