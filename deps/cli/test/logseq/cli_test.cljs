(ns logseq.cli-test
  (:require ["child_process" :as child-process]
            [cljs.test :refer [deftest is testing]]
            [clojure.string :as string]
            [datascript.core :as d]
            [logseq.cli.common.mcp.tools :as mcp-tools]))

(defn- sh
  "Run shell cmd synchronously and silently. Stdout/stderr can be inspected as needed"
  [cmd]
  (child-process/spawnSync (first cmd)
                           (clj->js (rest cmd))
                           #js {:stdio "pipe"}))

(deftest basic-help
  (let [start-time (cljs.core/system-time)
        result (sh ["node" "cli.mjs" "--help"])
        end-time (cljs.core/system-time)]

    (is (string/includes? (str (.-stdout result))
                          "Usage: logseq [command]"))

    (let [max-time (-> 0.40 (* (if js/process.env.CI 2 1)))]
      (is (< (-> end-time (- start-time) (/ 1000)) max-time)
          (str "Printing CLI help takes less than " max-time "s")))))

(deftest get-page-data-returns-blocks-for-pages-with-content
  (testing "getPage serializes blocks from pages with children"
    (let [page-uuid (random-uuid)
          block-uuid (random-uuid)
      db (d/db-with
          (d/empty-db {:block/uuid {:db/unique :db.unique/identity}
                       :block/name {:db/index true}
                       :block/page {:db/index true
                                    :db/valueType :db.type/ref}
                       :block/parent {:db/valueType :db.type/ref}})
          [{:db/id -1
            :block/uuid page-uuid
            :block/name "test-page"
            :block/title "Test Page"}
           {:db/id -2
            :block/uuid block-uuid
            :block/title "Child block"
            :block/order "a0"
            :block/page -1
            :block/parent -1}])
          result (mcp-tools/get-page-data db "test-page")]
      (is (= "Test Page" (get-in result [:entity :block/title])))
      (is (= 1 (count (:blocks result))))
      (is (every? map? (:blocks result))))))
