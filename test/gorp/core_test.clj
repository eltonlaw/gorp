(ns gorp.core-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [are deftest testing is]]
            [gorp.core :as gorp])
  (:import [java.io File]))

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
         "{\"a\":1,\"b\":\"foo\",\"date\":\"2022-11-01T00:00:00Z\"}"
         {:fmt :json}

         "{\n  \"a\" : 1,\n  \"b\" : \"foo\",\n  \"date\" : \"2022-11-01T00:00:00Z\"\n}"
         {:fmt :json :pretty? true}

         "{:a 1, :b \"foo\", :date #inst \"2022-11-01T00:00:00.000-00:00\"}"
         {:fmt :edn}

         "{:a 1, :b \"foo\", :date #inst \"2022-11-01T00:00:00.000-00:00\"}\n"
         {:fmt :edn :pretty? true}))
  (let [xml-data {:tag :SomeElement
                  :attrs {"xmlns" "somens"}
                  :content [{:tag :Bar :attrs {} :content ["1"]}
                            {:tag :Baz :attrs {} :content ["2"]}]}]
    (is (= "<?xml version=\"1.0\" encoding=\"UTF-8\"?><SomeElement xmlns:a=\"somens\"><Bar>1</Bar><Baz>2</Baz></SomeElement>"
           (gorp/write-str xml-data {:fmt :xml})))
    (is (= "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<SomeElement xmlns:a=\"somens\">\n  <Bar>1</Bar>\n  <Baz>2</Baz>\n</SomeElement>\n"
           (gorp/write-str xml-data {:fmt :xml :pretty? true})))))

(deftest read-write-file-roundtrip
  (let [f (File/createTempFile "gorp_tests_roundtrip" ".edn")
        fp (.getPath f)
        data {:a 1 :foo "bar"}]
    (gorp/write-file fp data)
    (is (= (gorp/read-file fp) data)))
  (let [f (File/createTempFile "gorp_tests_roundtrip" ".json")
        fp (.getPath f)
        data {:a 1 :foo "bar"}]
    (gorp/write-file fp data)
    (is (= (gorp/read-file fp) data)))
  (let [f (File/createTempFile "gorp_tests_roundtrip" ".xml")
        fp (.getPath f)
        data #xml/element{:tag :A :content ["BC"]}]
    (gorp/write-file fp data)
    (is (= (gorp/read-file fp) data))))

(deftest read-file-fmt-sql-test
  (is (= "SELECT col1, col2, col3 FROM tableA WHERE col1 = 'b';"
         (gorp/read-file (io/resource "some_query.sql")))))
