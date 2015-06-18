
(ns clojure.core.typed.test.data
  (:require  [clojure.core.typed :as t] 
             [clojure.test :refer :all]                
             [clojure.core.typed.test.test-utils :refer :all]))

(deftest diff-test
  (is-tc-e   (diff '[1 2 3] '[1 2]) (U (Vec [Any *] ) (Seq Any))
             :requires [[clojure.data :refer [diff]]]) 
  (is-tc-err   (diff '[1 2 3] '[1 2]) (t/List t/Any )
             :requires [[clojure.data :refer [diff]]]))
