(ns frontend.handler.route-test
  (:require [clojure.test :refer [deftest is use-fixtures testing]]
            [frontend.db.utils :as db-utils]
            [frontend.handler.route :as route-handler]
            [frontend.test.helper :as test-helper :refer [load-test-files]]))

(use-fixtures :each {:before test-helper/start-test-db!
                     :after test-helper/destroy-test-db!})

(deftest redirect-to-page-helper-test
  (testing "valid-page-ref? accepts uuids and non-blank strings"
    (is (true? (#'route-handler/valid-page-ref? (random-uuid))))
    (is (true? (#'route-handler/valid-page-ref? "home")))
    (is (false? (#'route-handler/valid-page-ref? "")))
    (is (false? (#'route-handler/valid-page-ref? nil))))

  (testing "build-page-query-params builds anchor params and lets explicit anchor win"
    (is (= {:anchor "ls-block-b1"}
           (#'route-handler/build-page-query-params {:block-id "b1"})))
    (is (= {:anchor "custom-anchor"}
           (#'route-handler/build-page-query-params {:block-id "b1"
                                                     :anchor "custom-anchor"})))
    (is (nil? (#'route-handler/build-page-query-params {}))))

  (testing "build-page-redirect adds optional query params and push flag"
    (is (= {:to :page
            :path-params {:name "page"}}
           (#'route-handler/build-page-redirect "page" {})))
    (is (= {:to :page
            :path-params {:name "page"}
            :query-params {:anchor "ls-block-b2"}}
           (#'route-handler/build-page-redirect "page" {:block-id "b2"})))
    (is (= {:to :page
            :path-params {:name "page"}
            :query-params {:anchor "anchor-1"}
            :push false}
           (#'route-handler/build-page-redirect "page" {:anchor "anchor-1"
                                                        :push false})))))

(deftest default-page-route
  (let [journal-uuid (random-uuid)]
    (load-test-files
     [{:page {:block/title "foo"}
       :blocks
       [{:block/title "b1"}
        {:block/title "B1"
         :build/properties {:logseq.property/heading 2}}
        {:block/title "b2"}
        {:block/title "Header 2"
         :build/properties {:logseq.property/heading 3}}
        {:block/title (str "Header 3 [[" journal-uuid "]]")
         :build/properties {:logseq.property/heading 2}}]}
      {:page {:build/journal 20221219
              :build/keep-uuid? true
              :block/uuid journal-uuid}}]))

  (testing ":page-block route"
    (let [block (ffirst
                 (db-utils/q '[:find (pull ?b [:block/uuid])
                               :where [?b :block/title "B1"]]))]
      (is (= {:to :page-block
              :path-params {:name "foo" :block-route-name "b1"}}
             (#'route-handler/default-page-route (:block/uuid block)))
          "Generates a page-block link if route-name is found")))

  (let [block (ffirst
               (db-utils/q '[:find (pull ?b [:block/uuid])
                             :where [?b :block/title "Header 2"]]))]
    (is (= {:to :page-block
            :path-params {:name "foo" :block-route-name "header 2"}}
           (#'route-handler/default-page-route (:block/uuid block)))
        "Generates a page-block link for route-name with whitespace and properties is found")

    (let [block (test-helper/find-block-by-content #"Header 3")]
      (is (= {:to :page-block
              :path-params {:name "foo" :block-route-name "header 3 [[dec 19th, 2022]]"}}
             (#'route-handler/default-page-route (:block/uuid block)))
          "Generates a page-block link for route name with page ref")))

  (testing ":page route"
    (let [uuid (-> (db-utils/q '[:find (pull ?b [:block/uuid])
                                 :where [?b :block/title "b2"]])
                   ffirst
                   :block/uuid)]
      (is (= {:to :page :path-params {:name (str uuid)}}
             (#'route-handler/default-page-route uuid))
          "Generates a page link if route-name is not found"))

    (is (= {:to :page :path-params {:name "page-name"}}
           (#'route-handler/default-page-route "page-name"))
        "Generates a page link if name is not a uuid")

    (is (= {:to :page :path-params {:name "page name"}}
           (#'route-handler/default-page-route "Page name"))
        "Generates a case insensitive page link")))
