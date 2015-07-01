(ns clojure.core.typed.test.testnew
  (:require  [clojure.core.typed :as t] 
             [clojure.test :refer :all]                
             [clojure.core.typed.test.test-utils :refer :all]))

(deftest runTest-test
  (is-tc-e   #(run-tests) (t/Map t/Any t/Any)
             :requires [[clojure.test :refer [run-tests]]]))



