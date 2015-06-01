(ns test.core-test
(:require [clojure.core.typed :as t]
                [clojure.test :refer :all]
                [test.core]))

(deftest check-test
  (is (t/check-ns 'test.check)))
