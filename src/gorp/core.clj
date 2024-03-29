(ns gorp.core
  (:require
    [cheshire.core :as ch]
    [clj-async-profiler.core :as prof]
    [clojure.data.csv :as csv]
    [clojure.data.xml :as xml]
    [clojure.data.xml.node :as xml.node]
    [clojure.data.xml.name :as xml.name]
    [clojure.data.xml.event :as xml.event]
    [clojure.data.xml.pu-map :as xml.pu-map]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.pprint :as pprint]
    [clojure.repl :as repl]
    [clojure.set :as set]
    [clojure.string :as string]
    [clj-uuid :as uuid]
    [clojure.walk :as walk]
    [criterium.core :as criterium]
    [reply.main :as reply.main])
  (:import
    [clojure.lang RT]
    [java.io File]
    [java.net URL]))

(defmulti read-str
  (fn ([s opts] (:fmt opts))
      ([s] nil)))
(defmethod read-str :txt [s _] s)
(defmethod read-str :edn [s _]
  (let [x (edn/read-string s)]
    (when-not (instance? clojure.lang.Symbol x)
      x)))
(defmethod read-str :json [s _] (ch/parse-string s true))
(defmethod read-str :xml [s _] (xml/parse-str s))
(defmethod read-str :txt [s _] s)
(defmethod read-str :sql [s _]
  (string/replace
    (->> (string/split s #"\n")
         (remove #(string/starts-with? % "--"))
         (string/join " "))
    #"\s+"
    " "))
(defmethod read-str :csv [s _]
  (let [rows-strs (csv/read-csv s)
        headers (map keyword (first rows-strs))]
    (mapv zipmap (repeat headers) (rest rows-strs))))
(defmethod read-str nil [s]
  (some #(try (read-str s {:fmt %}) (catch Throwable _ nil))
        ;; FIXME: figure out better way of getting this list
        [:edn :json :xml]))

(defmulti write-str
  "Generate str from clj data"
  {:arglists '([x opts])}
  (fn [_x opts] (:fmt opts)))
(defmethod write-str :txt [x _] x)
(defmethod write-str :json [x opts]
  (ch/generate-string
    x
    (set/rename-keys opts
                     {:pretty? :pretty})))
(defmethod write-str
  :xml [x {:keys [pretty?]}]
  (let [element (cond
                  (= "clojure.data.xml.Element" (type x)) x
                  (map? x) (xml.node/map->Element x)
                  :else (throw (ex-info "Couldn't coerce element into XML"
                                        {:xml-data x})))]
    (if pretty?
      (xml/indent-str element)
      (xml/emit-str element))))
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
    (pr-str x)))
(defmethod write-str :csv
  [x {:keys [headers]}]
  (assert (and (not-empty headers)
               (every? #(or (string? %)
                            (keyword? %)) headers))
          "Passed in :headers should be a list of keywords/strings")
  (let [make-row (apply juxt headers)]
    (with-open [s (java.io.StringWriter.)]
      (csv/write-csv s
                     (cons (map name headers)
                           (map make-row x)))
      (str s))))

(defn read-edn-file [fp]
  (read-str (slurp fp) {:fmt :edn}))

(defn read-json-file [fp]
  (read-str (slurp fp) {:fmt :json}))

(defn file-ext [x]
  (cond
    (string? x) (keyword (last (string/split x #"\.")))
    (instance? File x) (file-ext (.getPath ^File x))
    (instance? URL x) (file-ext (.getPath ^URL x))
    :else (throw (ex-info (str "Unexpected input passed into file-ext:" (class x))
                          {:class (class x) :x x}))))

(defn read-file
  ([fp] (read-file fp {}))
  ([fp opts]
   (read-str (slurp fp) (conj {:fmt (file-ext fp)} opts))))

(defn try-read-file
  ([fp] (try-read-file fp {}))
  ([fp read-file-opts]
   (try (read-file fp read-file-opts) (catch Exception _ nil))))

(defn list-files
  ([root-fp] (list-files root-fp ".*"))
  ([root-fp glob]
   (let [grammar-matcher (.getPathMatcher
                           (java.nio.file.FileSystems/getDefault)
                           (format "regex:%s" glob))]
     (->> (io/file root-fp)
          file-seq
          (filter #(.isFile %))
          (filter #(.matches grammar-matcher (.getFileName (.toPath %))))))))

(defn read-files
  "Attempts to read all files in some dir
  Ex. (read-files \"my-dir\" \".*.clj\")"
  [root-fp regex-str]
  (mapv #(read-file %) (list-files root-fp regex-str)))

(defn write-txt-file
  "Dump unformatted value into a file"
  [fp s]
  (println "..... Writing new: " fp)
  (when-not (.exists (io/file fp))
    (io/make-parents fp))
  (spit fp s))

(defn write-file
  ([fp x]
   (write-file fp x {}))
  ([fp x opts]
   (write-txt-file fp (write-str x (conj {:fmt (file-ext fp)
                                          :pretty? true}
                                         opts)))))

(defn write-edn-file [fp x]
  (write-txt-file fp (write-str x {:fmt :edn :pretty? true})))

(defn write-xml-file
  "Writes enlive formatted xml map as xml file"
  [fp x]
  (write-txt-file fp (write-str x {:fmt :xml :pretty? true})))

(defn write-json-file
  "Write some json object as json file"
  [fp x]
  (write-txt-file (write-str x {:fmt :json :pretty? true})))

(defn run-or-cached
  "Either return data read from a file or run f, save to file and return f's ret"
  [{:keys [cache-fp
           cache-on-nil?
           force-run?
           read-file-opts
           write-file-opts]
    :or {cache-on-nil? true}}
   f]
  (assert cache-fp "Need to pass in cache-fp")
  (if-let [ret (and (not force-run?)
                    (try-read-file cache-fp read-file-opts))]
    ret
    (let [ret (f)]
      (if (and (false? cache-on-nil?) (nil? ret))
        (println "..... nil ret for cache-fp: " cache-fp)
        (do (write-file cache-fp ret) ret))
      ret)))

(defn get-shape
  "For some nested coll, print out data types, keeping keys
  as is.

  Pass in :dedupe? to vectors and lists are deduped by shape"
  ([x] (get-shape x nil))
  ([x {:keys [dedupe?] :as opts}]
   (if-not (coll? x)
     (type x)
     (cond
       (map? x) (reduce-kv (fn [m k v]
                             (assoc m k (get-shape v opts))) {} x)
       (set? x) (into #{} (map #(get-shape % opts) x))
       (vector? x) (cond-> (mapv #(get-shape % opts) x)
                     dedupe? (-> distinct vec))
       (list? x) (cond-> (map #(get-shape % opts) x)
                   dedupe? (distinct))))))

(defn pprint-shape
  ([input] (pprint-shape input nil))
  ([input opts]
   (pprint/pprint (get-shape input opts))))

(extend
  clojure.lang.PersistentArrayMap
  {:gen-event (fn elem-gen-event [{:keys [tag attrs content] :as element}]
                (xml.name/separate-xmlns
                 attrs #((if (seq content)
                           xml.event/->StartElementEvent
                           xml.event/->EmptyElementEvent)
                         tag %1
                         (xml.pu-map/merge-prefix-map
                           (xml.event/element-nss* element) %2)
                         nil)))
   :next-events (fn elem-next-events [{:keys [tag content]} next-items]
                  (if (seq content)
                    (list* content xml.event/end-element-event next-items)
                    next-items))})

(defn count-by [f coll]
  (frequencies (map f coll)))
