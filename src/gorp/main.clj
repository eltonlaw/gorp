(ns gorp.main
  (:require
    [reply.main :as reply.main]))

(defn init-fn []
  (println "Loading gorp.core...")
  (require '[gorp.core])
  (in-ns 'gorp.core))

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
