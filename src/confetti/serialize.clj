(ns confetti.serialize
  (:require [clojure.java.io :as io]))

;; File map de/serialization ---------------------------------------------------
;; https://github.com/boot-clj/boot/issues/250

(defn coerce-to-string [x]
  (cond
    (instance? java.io.File x) (.getCanonicalPath x)
    (string? x)                x
    :else (throw (ex-info ":file key must be file or string."
                          {:value x}))))

(defn ->str [file-maps]
  (mapv #(update % :file coerce-to-string) file-maps))

(defn ->file [file-maps]
  (mapv #(update % :file io/file) file-maps))

