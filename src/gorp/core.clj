(ns gorp.core
  (:require [rebel-readline.clojure.main :as rebel]))

(defn -main [& args]
  (apply rebel/-main args))
