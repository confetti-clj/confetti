(ns confetti.serialize
  (:require [clojure.java.io :as io]))

;; File map de/serialization ---------------------------------------------------
;; https://github.com/boot-clj/boot/issues/250

(defn ->str [file-maps]
  (mapv (fn [fm] (update fm :file #(.getCanonicalPath %))) file-maps))

(defn ->file [file-maps]
  (mapv #(update % :file io/file) file-maps))

