(ns clojure.core.typed.test.template
  (:require 	[clojure.core.typed :as t]
                [clojure.template :as template] 
		[clojure.xml :as xml]
		[clojure.zip :as zip]
		[clojure.test :refer :all]                
		[clojure.core.typed.test.test-utils :refer :all]))

(deftest applytemplate-test
(is-tc-e (clojure.template/apply-template '[a b c d e] '[d a b e c e b a d] '(1 2 3 4 5))t/Any) 
(is-tc-err (clojure.template/apply-template '[a b c d e] '[d a b e c e b a d] '(1 2 3 4 5)) t/Str))
