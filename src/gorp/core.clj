(ns gorp.core
  (:require
    [cheshire.core :as ch]
    [clj-async-profiler.core :as prof]
    [clojure.data.xml :as xml]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.set :as set]
    [clojure.pprint :as pprint]
    [clojure.repl :as repl]
    [clojure.string :as string]
    [clj-uuid :as uuid]
    [clojure.walk :as walk]
    [criterium.core :as criterium]
    [reply.main :as reply.main])
  (:import
    [clojure.lang RT]))

(defmulti read-str
  (fn ([s opts] (:fmt opts))
      ([s] nil)))
(defmethod read-str :edn [s _]
  (let [x (edn/read-string s)]
    (when-not (instance? clojure.lang.Symbol x)
      x)))
(defmethod read-str :json [s _] (ch/parse-string s true))
(defmethod read-str nil [s]
  (some #(try (read-str s {:fmt %}) (catch Throwable _ nil))
        ;; FIXME: figure out better way of getting this list
        [:edn :json]))

(defmulti write-str (fn [_x opts] (:fmt opts)))
(defmethod write-str :json [x opts]
  (ch/generate-string x (set/rename-keys opts
                                         {:pretty? :pretty})))
(defmethod write-str :xml [x _]
  (xml/indent-str x))
(defmethod write-str :edn
  [x {:keys [pretty?]}]
  (if pretty?
    (binding [pprint/*print-right-margin* 120]
      (with-out-str
        (->> (walk/postwalk (fn [y]
                              (if (map? y)
                                (into (sorted-map) y)
                                y))
                            x)
             (pprint/pprint))))
    x))

(defn read-edn-file [fp]
  (read-str (slurp fp) {:fmt :edn}))

(defn read-json-file [fp]
  (read-str (slurp fp) {:fmt :json}))

(defn read-files [ext fp]
  (let [grammar-matcher (.getPathMatcher (java.nio.file.FileSystems/getDefault)
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

(defn write-txt-file
  "Dump unformatted value into a file"
  [fp s]
  (println "..... Writing new: " fp)
  (when-not (.exists (io/file fp))
    (io/make-parents fp))
  (spit fp s))

(defn write-edn-file [fp x]
  (write-txt-file fp (write-str x {:fmt :edn :pretty? true})))

(defn write-xml-file
  "Writes enlive formatted xml map as xml file"
  [fp x]
  (write-txt-file fp (write-str x {:fmt :xml})))

(defn write-json-file
  "Write some json object as json file"
  [fp x]
  (write-txt-file (write-str x {:fmt :json :pretty? true})))
