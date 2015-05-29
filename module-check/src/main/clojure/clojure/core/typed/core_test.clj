(ns test.core-test
 (:use clojure.test)
 (:require [clojure.core.typed :as t])
 (:require test.core) 
)

(deftest template-test
  (is (t/check-ns 'test.template)))
