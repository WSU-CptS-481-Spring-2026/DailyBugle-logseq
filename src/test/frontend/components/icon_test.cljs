(ns frontend.components.icon-test
  (:require [cljs.test :refer [deftest is testing]]
            [frontend.components.icon :as icon]
            [frontend.ui :as ui]))

(deftest icon-normalization-and-fallback
  (testing "normalizes string and keyword inputs to tabler icon rendering"
    (with-redefs [ui/icon (fn [id opts] [:ui-icon id opts])]
      (is (= [:ui-icon "settings" {:size 12}]
             (icon/icon "settings" {:size 12})))
      (is (= [:ui-icon "settings" {}]
             (icon/icon :settings)))))

  (testing "color wrapper is applied only when requested"
    (with-redefs [ui/icon (fn [id opts] [:ui-icon id opts])]
      (is (= [:span.inline-flex.items-center.ls-icon-color-wrap
              {:style {:color "inherit"}}
              [:ui-icon "settings" {}]]
             (icon/icon "settings" {:color? true})))
      (is (= [:span.inline-flex.items-center.ls-icon-color-wrap
              {:style {:color "#3366ff"}}
              [:ui-icon "settings" {}]]
             (icon/icon {:type :tabler-icon :id "settings" :color "#3366ff"}
                        {:color? true})))))

  (testing "returns fallback node for invalid icons"
    (is (= [:span] (icon/icon {:type :unknown :id "x"})))
    (is (= [:span] (icon/icon {:type :emoji :id ""}))))

  (testing "emoji icon rendering remains supported"
    (is (= [:span.ui__icon
            [:em-emoji {:id "sparkles"
                        :style {:line-height 1}}]]
           (icon/icon {:type :emoji :id "sparkles"})))))

(deftest normalize-tabs
  (testing "limits tabs and default tab selection"
    (let [{:keys [tabs default-tab has-icon-tab?]}
          (#'icon/normalize-tabs [[:emoji "Emojis"]] nil)]
      (is (= [[:emoji "Emojis"]] tabs))
      (is (= :emoji default-tab))
      (is (false? has-icon-tab?)))))

(deftest emoji-sections
  (testing "includes frequently used before emojis when enabled"
    (let [used [{:id "star" :type :emoji}
                {:id "alert-circle" :type :tabler-icon}]
          emojis [{:id "a"} {:id "b"}]
          sections (#'icon/emoji-sections emojis used true)]
      (is (= ["Frequently used" "Emojis (2)"]
             (map :title sections)))
      (is (= [{:id "star" :type :emoji}]
             (-> sections first :items))))))

(deftest emoji-sections-layout
  (testing "frequently used uses non-virtual list while emojis remain virtual"
    (let [used [{:id "star" :type :emoji}]
          emojis [{:id "a"}]
          sections (#'icon/emoji-sections emojis used true)]
      (is (false? (-> sections first :virtual-list?)))
      (is (true? (-> sections second :virtual-list?))))))
