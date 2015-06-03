(ns test.check
  (:require 	[clojure.core.typed :as t]
              [clojure.template :as template] 
		          [clojure.xml :as xml]
		          [clojure.zip :as zip]
		          [clojure.test :refer :all]                
		          [clojure.core.typed.test.test-utils :refer [is-tc-e]]))


(deftest check-test
  (is-tc-e (apply-template '[a b c d e] '[d a b e c e b a d] '(1 2 3 4 5)) (t/Any))
)
