(ns clojure.core.typed.test.set_new
  (:require [clojure.core.typed :as t]
            [clojure.set :as set] 
		        [clojure.test :refer :all]                
		        [clojure.core.typed.test.test-utils :refer :all]))

(deftest subset-test
 (is-tc-e (subset? #{1} #{2}) Boolean 
	:requires [[clojure.set :refer [subset?]]])
 (is-tc-err (subset? #{1} #{2}) (t/Set t/Any) 
	:requires [[clojure.set :refer [subset?]]]))

(deftest superset-test
 (is-tc-e (superset? #{1} #{2}) Boolean 
	:requires [[clojure.set :refer [superset?]]])
 (is-tc-err (superset? #{1} #{2}) (t/Set t/Any) 
	:requires [[clojure.set :refer [superset?]]]))

(deftest join-test
 (is-tc-e (join #{ {:a 1} {:a 2} } #{ {:b 1} {:b 2} }) (t/Set (t/Map t/Any t/Any)) 
	:requires [[clojure.set :refer [join]]])
 (is-tc-err (join #{ {:a 1} {:a 2} } #{ {:b 1} {:b 2} }) (t/Vec t/Any)
	:requires [[clojure.set :refer [join]]]))
	
	
