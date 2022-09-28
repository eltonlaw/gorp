(ns gorp.main
  (:require
    [clj-async-profiler.core :as prof]
    [clojure.java.io :as io]
    [clojure.main :as main]
    [reply.main :as reply.main]))

(def init-file "gorp_init.clj")

(defn init-fn []
  (println "Loading gorp.core...")
  (require '[gorp.core])
  (in-ns 'gorp.core)
  ;; Load a custom script if exists.
  ;; NOTE: it takes the first found, doesn't run everything available.
  ;; easier to choose to run more scripts if needed
  (when-let [fp (some #(when (.exists (io/file %)) %)
                      [init-file
                       (str (System/getenv "XDG_CONFIG_HOME") "/" init-file)
                       (str (System/getenv "HOME") "/" init-file)])]
    (main/load-script fp)))

(defn -main
  "Mostly a copy of reply.main/-main except with a hardcoded
  :custom-init in resources/gorp_init.clj which just calls the
  `init-fn` defined above"
  [& args]
  (let [[options args banner]
        (try (reply.main/parse-args args)
          (catch Exception e
            (println (.getMessage e))
            (reply.main/parse-args ["--help"])))
        options (assoc options
                       :custom-eval '(do (require '[gorp.main])
                                         (gorp.main/init-fn)))]
    (if (:help options)
      (println banner)
      (reply.main/launch options))
    (shutdown-agents)))
