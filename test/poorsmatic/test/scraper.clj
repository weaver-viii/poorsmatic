(ns poorsmatic.test.scraper
  (:use clojure.test
        poorsmatic.scraper))

(deftest count-word-matches
  (let [count-foo (counter "foo")
        request {:body "a test for foo, Foo, or FOO, but not food"}]
    (is (= 3 (:count (count-foo request))))))

