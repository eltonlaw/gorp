(ns gorp.core
  (:require [clojure.edn :as edn]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [clojure.walk :as walk]
            [clojure.pprint :as pprint]
            [clojure.data.xml :as xml]
            [cheshire.core :as ch]
            [cognitect.aws.client.api :as aws]
            [reply.main :as reply]))

(defn parse-json [s]
  (ch/parse-string s true))
(def parse-edn edn/read-string)

(defn read-edn-file [fp]
  (parse-edn (slurp fp)))

(defn read-json-file [fp]
  (parse-json (slurp fp)))

(defn read-files [ext fp]
  (let [grammar-matcher (.getPathMatcher
                         (java.nio.file.FileSystems/getDefault)
                         (format "glob:*.{%s}" ext))
        read-file-fn (case ext
                       "edn" read-edn-file
                       "json" read-json-file)]
    (->> (io/file fp)
         file-seq
         (filter #(.isFile %))
         (filter #(.matches grammar-matcher (.getFileName (.toPath %))))
         (mapv read-file-fn))))

(def read-edn-files (partial read-files "edn"))
(def read-json-files (partial read-files "json"))

(defn write-edn-file [fp m]
  (println "..... Writing new: " fp)
  (when-not (.exists (io/file fp))
    (io/make-parents fp))
  (let [out (walk/postwalk (fn [x]
                             (if (map? x)
                               (into (sorted-map) x)
                               x))
                           m)]
    (binding [pprint/*print-right-margin* 150]
      (spit fp (with-out-str (pprint/pprint out))))))

(defn write-txt-file
  "Dump unformatted value into a file"
  [fp s]
  (println "..... Writing new: " fp)
  (spit fp s))

(defn write-xml-file
  "Writes enlive formatted xml map as xml file"
  [fp m]
  (println "..... Writing new: " fp)
  (when-not (.exists (io/file fp))
    (io/make-parents fp))
  (spit fp (xml/indent-str m)))

(defn write-json-file
  "Write some json object as json file"
  [fp x]
  (println "..... Writing new: " fp)
  (spit fp (ch/generate-string x {:pretty true})))

(defn -main [& args]
  (apply reply/-main args))
