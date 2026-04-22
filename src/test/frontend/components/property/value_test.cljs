(ns frontend.components.property.value-test
  (:require [cljs.test :refer [deftest is testing]]
            [frontend.components.property.value :as property-value]))

(deftest append-choice-once-test
  (testing "does not append the same entity twice"
    (let [existing [{:db/id 1 :block/title "topic-a"}
                    {:value {:db/id 2 :block/uuid "topic-b"} :label "topic-b"}]]
      (is (= existing
             (property-value/append-choice-once existing {:value {:db/id 2 :block/uuid "topic-b"} :label "topic-b"})))))

  (testing "appends a new entity once"
    (let [existing [{:db/id 1 :block/title "topic-a"}]
          appended (property-value/append-choice-once existing {:value {:db/id 2 :block/uuid "topic-b"} :label "topic-b"})]
      (is (= 2 (count appended)))
      (is (= [1 2] (map property-value/choice-identity appended))))))
