(ns frontend.handler.export.common
  "common fns for exporting.
  exclude some fns which produce lazy-seq, which can cause strange behaviors
  when use together with dynamic var."
  (:refer-clojure :exclude [map filter mapcat concat remove])
  (:require [clojure.string :as string]
            [frontend.db.conn :as conn]
            [frontend.extensions.calc :as calc]
            [frontend.state :as state]
            [logseq.cli.common.export.common :as cli-export-common]
            [promesa.core :as p]))

(defn- string->lines
  [s]
  (let [trailing-newline? (string/ends-with? s "\n")
        lines (string/split s #"\n" -1)
        lines (if trailing-newline? (pop lines) lines)]
    {:lines lines
     :trailing-newline? trailing-newline?}))

(defn- lines->string
  [lines trailing-newline?]
  (let [s (string/join "\n" lines)]
    (if trailing-newline?
      (str s "\n")
      s)))

(defn- calc-results->aligned-lines
  [lines]
  (let [results (calc/eval-lines (string/join "\n" lines))
        max-width (reduce max 0 (mapv (fn [line]
                                        (if (string/blank? line) 0 (count line)))
                                      lines))]
    (mapv (fn [idx line]
            (if (string/blank? line)
              line
              (let [result (nth results idx nil)
                    result (cond
                             (nil? result) ""
                             (calc/failure? result) "?"
                             :else (str result))]
                (str line
                     (apply str (repeat (inc (- max-width (count line))) " "))
                     "= "
                     result))))
          (range (count lines))
          lines)))

(defn transform-calc-mode-content-with-results
  [content]
  (let [{:keys [lines trailing-newline?]} (string->lines (or content ""))
        lines* (calc-results->aligned-lines lines)]
    (lines->string lines* trailing-newline?)))

(defn get-content-config
  ([]
   (get-content-config nil))
  ([other-options]
   (let [transform-calc-mode-content-fn (when (:include-calc-results other-options)
                                          transform-calc-mode-content-with-results)]
     (cond-> {:export-bullet-indentation (state/get-export-bullet-indentation)}
       transform-calc-mode-content-fn
       (assoc :transform-calc-mode-content-fn transform-calc-mode-content-fn)))))

(defn root-block-uuids->content
  "Converts given block uuids to content for given repo"
  ([repo root-block-uuids]
   (root-block-uuids->content repo root-block-uuids nil))
  ([repo root-block-uuids other-options]
   (binding [cli-export-common/*current-db* (conn/get-db repo)
             cli-export-common/*content-config* (get-content-config other-options)]
     (let [contents (mapv (fn [id]
                            (cli-export-common/get-blocks-contents id)) root-block-uuids)]
       (string/join "\n" (mapv string/trim-newline contents))))))

(defn get-page-content
  "Gets page content for current repo, db and state"
  ([page-uuid]
   (get-page-content page-uuid nil))
  ([page-uuid other-options]
   (binding [cli-export-common/*current-db* (conn/get-db (state/get-current-repo))
             cli-export-common/*content-config* (get-content-config other-options)]
     (cli-export-common/get-page-content page-uuid))))

(defn <get-debug-datoms
  [repo]
  (state/<invoke-db-worker :thread-api/export-get-debug-datoms repo))

(defn <get-all-page->content
  [repo options]
  (state/<invoke-db-worker :thread-api/export-get-all-page->content repo options))

(defn <get-file-contents
  [repo suffix]
  (p/let [page->content (<get-all-page->content repo
                                                {:export-bullet-indentation (state/get-export-bullet-indentation)})]
    (clojure.core/map (fn [[page-title content]]
                        {:path (str page-title "." suffix)
                         :content content
                         :title page-title
                         :format :markdown})
                      page->content)))

(defn- src-lines->logical-lines
  [src-lines]
  (string->lines (apply str src-lines)))

(defn- logical-lines->src-lines
  [lines trailing-newline?]
  (let [src-lines (vec (interpose "\n" lines))]
    (if trailing-newline?
      (conj src-lines "\n")
      src-lines)))

(defn transform-src-lines-with-calc-results
  [language src-lines]
  (if (and (= "calc" (some-> language string/trim string/lower-case))
           (seq src-lines))
    (let [{:keys [lines trailing-newline?]} (src-lines->logical-lines src-lines)
          lines* (calc-results->aligned-lines lines)]
      (logical-lines->src-lines lines* trailing-newline?))
    src-lines))

(defn build-transform-src-lines-fn
  [other-options]
  (when (:include-calc-results other-options)
    transform-src-lines-with-calc-results))

;; Aliased fns requiring cli-export-common dynamic bindings e.g. cli-export-common/*current-db*
(def replace-block&page-reference&embed cli-export-common/replace-block&page-reference&embed)
(def replace-Heading-with-Paragraph cli-export-common/replace-Heading-with-Paragraph)

;; Aliased fns
(def priority->string cli-export-common/priority->string)
(def timestamp-to-string cli-export-common/timestamp-to-string)
(def hashtag-value->string cli-export-common/hashtag-value->string)
(def remove-block-ast-pos cli-export-common/remove-block-ast-pos)
(def Properties-block-ast? cli-export-common/Properties-block-ast?)
(def keep-only-level<=n cli-export-common/keep-only-level<=n)
(def remove-emphasis cli-export-common/remove-emphasis)
(def remove-page-ref-brackets cli-export-common/remove-page-ref-brackets)
(def remove-tags cli-export-common/remove-tags)
(def remove-prefix-spaces-in-Plain cli-export-common/remove-prefix-spaces-in-Plain)
(def walk-block-ast cli-export-common/walk-block-ast)
