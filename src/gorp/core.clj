(ns gorp.core
  (:require
    [cheshire.core :as ch]
    [clj-async-profiler.core :as prof]
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

(defn read-files [ext fp]
  (let [grammar-matcher (.getPathMatcher
                          (java.nio.file.FileSystems/getDefault)
                          (format "glob:*.{%s}" ext))]
    (->> (io/file fp)
         file-seq
         (filter #(.isFile %))
         (filter #(.matches grammar-matcher (.getFileName (.toPath %))))
         (mapv #(read-files ext %)))))

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
  ([fp x {:keys [pretty?]
          :or {pretty? true}}]
   (write-txt-file fp (write-str x {:fmt (file-ext fp)
                                    :pretty? pretty?}))))

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
  [{:keys [cache-fp read-file-opts write-file-opts]} f]
  (assert cache-fp "Need to pass in cache-fp")
  (if-let [ret (try-read-file cache-fp read-file-opts)]
    ret
    (let [ret (f)]
      (write-file cache-fp ret)
      ret)))

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
