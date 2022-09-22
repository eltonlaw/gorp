(ns gorp.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [gorp.core :as gorp]))

(deftest read-str-test
  (testing "dont pass in ext, coerce by trying"
    (is (= {:a 1 :b "foo"}
           (gorp/read-str "{\"a\": 1, \"b\": \"foo\"}")))
    (is (= {:a 1 :b "foo"}
           (gorp/read-str "{:a 1 :b \"foo\"}")))
    (is (nil? (gorp/read-str "foo")))
    (is (nil? (gorp/read-str "_ ())")))))
