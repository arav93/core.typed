(ns clojure.core.typed.test.instant
  (:require  [clojure.core.typed :as t] 
             [clojure.test :refer :all]                
             [clojure.core.typed.test.test-utils :refer :all]))

(deftest read-instant-date-test
  (is-tc-e   (read-instant-date "2014-04-23T10:13Z") java.util.Date
             :requires [[clojure.instant :refer [read-instant-date]]])
  (is-tc-err   (read-instant-date "2014-04-23T10:13Z") String
             :requires [[clojure.instant :refer [read-instant-date]]])
   (is-tc-err   (read-instant-date 201404231013) String
             :requires [[clojure.instant :refer [read-instant-date]]]))
  
