(ns clojure.core.typed.test.template
  (:require 	[clojure.core.typed :as t]
                [clojure.template :as template] 
		[clojure.test :refer :all]                
		[clojure.core.typed.test.test-utils :refer :all]))

(deftest applyTemplate-test
  (is-tc-e(clojure.template/apply-template '[a b c d e] '[d a b e c e b a d] '(1 2 3 4 5))t/Any) 
  (is-tc-e(clojure.template/apply-template '[a b c d e] {:a 1 :b 2 :c 3 :d 4 :e 5} '(1 2 3 4 5))t/Any)
  (is-tc-e(clojure.template/apply-template '[a b c d e] 3 '(1 2 3 4 5))t/Any)
  (is-tc-err(clojure.template/apply-template '[a b c d e] '[d a b e c e b a d] '(1 2 3 4 5)) (t/Vec t/Any))
  (is-tc-err(clojure.template/apply-template '[a b c d e] '[d a b e c e b a d] '(1 2 3 4 5)) (t/HVec[t/Any]))
  (is-tc-err(clojure.template/apply-template '[a b c d e] '[d a b e c e b a d] '(1 2 3 4 5)) (t/List t/Any)))

