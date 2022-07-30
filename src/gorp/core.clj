(ns gorp.core
  (:require [reply.main :as reply]))

(defn -main [& args]
  (apply reply/-main args))
