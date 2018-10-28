(ns ankimo.common.worker.core
    (:require-macros [cljs.core.async.macros :refer [go]])
    (:require
        [dommy.core :as dommy :refer-macros [sel sel1]]
        [anki-cljs.core :as anki]
        [cljs.core.async :as async :refer [<! >!]]
        [cljs.core.match :refer-macros [match]]
        [ankimo.x.worker.browser :as browser]))


(defn create-button [text handler]
    ;; create
    (let [button (->
                     (dommy/create-element "a")
                     (dommy/set-style! "cursor" "pointer")
                     (dommy/add-class! :ankimo-add)
                     (dommy/set-text! text))]
        (dommy/listen! button :click handler)))

(defn extract-text
    [el selector]
    (->> (map dommy/text (sel el selector))
         (clojure.string/join ", ")))

(defn text-without-children [el]
    (-> (.-childNodes el)
        (aget 0)
        (dommy/text)))

(defn extract-kana [el]
    (let [el (sel1 el ".w-jpn-sm mark ruby")]
        (if (some? el)
            (text-without-children el)
            "")))

(defn extract-kanji [el]
    (let [defs (sel1 el ".w-jp dfn")
          ;; Some smaller entries don't have a designated kanji area, but a
          ;; ruby element
          ruby (sel1 defs "ruby")]

        (if (some? ruby)
            (text-without-children ruby)
            (-> (dommy/text defs)
                (clojure.string/replace "☆" "")
                (clojure.string/replace "★" "")))))

(defn extract-example [el]
    (let [examples (sel el ".w-ex .w-ex-p") ;; all examples
          example (if (> (count examples) 0)
                      (aget examples 0)
                      nil)]
        (if (some? example)
            (let [japanese (sel1 example ".w-ex-ja")
                  english (sel1 example ".w-ex-en")]
                ;; extract japanese
                ;; every word can either be a link, or just a piece of text

                {:en (dommy/text english)
                 :jp (->> (.-childNodes japanese)
                          array-seq ;; convert into array-seq so we can map over it
                          (map (fn [el]
                                   ;; if it's a text node, just return
                                   (if-let [is-text (= (type el) js/Text)]
                                       (dommy/text el)
                                       ;; if it has a ruby element, it has
                                       ;; nested info so try extract that
                                       (let [ruby (dommy/sel1 el "ruby")
                                             has-ruby? (some? ruby)]
                                           (if has-ruby? (text-without-children ruby) (dommy/text el))))))
                          clojure.string/join)})
            {:en "" :jp ""})))

(defn extract-example-japanese [el]
    (:jp (extract-example el)))

(defn extract-example-english [el]
    (:en (extract-example el)))

(defn add-to-anki [kanji kana english english-example japanese-example]
    (go
        (let [field-kanji (<! (browser/get-setting "field_kanji"))
              field-kana (<! (browser/get-setting "field_kana"))
              field-english (<! (browser/get-setting "field_english"))
              field-english-example (<! (browser/get-setting "field_english_example"))
              field-japanese-example (<! (browser/get-setting "field_japanese_example"))

              deck-name (<! (browser/get-setting "deck_name"))
              model-name (<! (browser/get-setting "model_name"))

              max-english (-> (<! (browser/get-setting "max_english_translations")) int)
              english-definitions (map clojure.string/trim (clojure.string/split english #";"))
              english-definitions-limited (clojure.string/join "; " (take max-english english-definitions))]
            (let [response (<! (anki/add-note deck-name model-name {field-kanji kanji
                                                                    field-kana kana
                                                                    field-english english-definitions-limited
                                                                    field-english-example english-example
                                                                    field-japanese-example japanese-example}
                                              [:ankimo :tangorin]))]
                (match response
                       [:ok _] true
                       [:error msg] (do (.alert js/window msg) false))))))

(defn pageLoad []
    (let [entries (sel ".entry")]
        (doseq [entry entries]
            (let [english-translations (sel entry ".w-dd .w-def li")
                  found-kana (extract-kana entry) ;; extract kana
                  found-kanji (extract-kanji entry) ;; extract kanji

                  ;; backup in case one of the 2 isn't found.
                  ;; Some words are only listed with hiragana since their Kanji
                  ;; isn't common. In these cases, just add the hiragana for
                  ;; both fields, or the other way around
                  kana (if (clojure.string/blank? found-kana) found-kanji found-kana)
                  kanji (if (clojure.string/blank? found-kanji) found-kana found-kanji)

                  example-english (extract-example-english entry) ;; extract english example
                  example-japanese (extract-example-japanese entry)  ;; extract japanese example
                  ]
                (doseq [english-el english-translations]
                    (let [english-text (text-without-children english-el)]
                        (->> (create-button " add to anki 🐠" (fn [el]
                                                                  (this-as this
                                                                           (do
                                                                               (let [previous-text (.-innerHTML this)]
                                                                                   (go
                                                                                       (aset this "innerHTML" " adding...")
                                                                                       (if (<! (add-to-anki kanji kana english-text example-english example-japanese))
                                                                                           (aset this "innerHTML" " added!")
                                                                                           (aset this "innerHTML" previous-text))))))))
                             (dommy/append! english-el))))))))

(.addEventListener js/window "load" pageLoad)

;; install timer
(defonce page-url (atom (.-URL js/document)))

(defonce timer (.setInterval js/window (fn []
                                           (let [current-url (.-URL js/document)]
                                               (if (not (= @page-url current-url))
                                                   (do
                                                       (reset! page-url current-url)
                                                       (.setTimeout js/window pageLoad 1000))))) ;; TODO: optimize this
                             500))
