(ns gorp.core-test
  (:require [clojure.test :refer [are deftest testing is]]
            [gorp.core :as gorp]))

(deftest read-str-test
  (testing "dont pass in ext, coerce by trying"
    (is (= {:a 1 :b "foo"}
           (gorp/read-str "{\"a\": 1, \"b\": \"foo\"}")))
    (is (= {:a 1 :b "foo"}
           (gorp/read-str "{:a 1 :b \"foo\"}")))
    (is (nil? (gorp/read-str "foo")))
    (is (nil? (gorp/read-str "_ ())")))))

(deftest write-str-test
  (let [data {:a 1 :b "foo" :date (java.util.Date. 122 10 1)}]
    (are [expected opts] (= expected (gorp/write-str data opts))
         "{\"a\":1,\"b\":\"foo\",\"date\":\"2022-11-01T04:00:00Z\"}"
         {:fmt :json}

         "{\n  \"a\" : 1,\n  \"b\" : \"foo\",\n  \"date\" : \"2022-11-01T04:00:00Z\"\n}"
         {:fmt :json :pretty? true}

         "{:a 1, :b \"foo\", :date #inst \"2022-11-01T04:00:00.000-00:00\"}"
         {:fmt :edn}

         "{:a 1, :b \"foo\", :date #inst \"2022-11-01T04:00:00.000-00:00\"}\n"
         {:fmt :edn :pretty? true})))
