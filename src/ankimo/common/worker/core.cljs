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
                (dommy/add-class! :ankimo-add)
                (dommy/set-text! text))]
    (dommy/listen! button :click handler)))

(defn extract-text
  [el selector]
  (->> (map dommy/text (sel el selector))
       (clojure.string/join ", ")))

(defn extract-kana [el] (extract-text el ".kana ruby rb"))
(defn extract-kanji [el] (extract-text el ".writing"))

(defn get-shortest-example [el selector]
  (let [els (sel el ".ex li") ;; all examples
        els (filter #(not (nil? (sel1 %1 ".ex-jap"))) els)] ;; only when it has a japanese text
    (when
        (> (count els) 0) ;; only when we actually have examples
      (->
       (reduce (fn [shortest current]
                 (let [jp1 (dommy/text (sel1 shortest ".ex-jap"))
                       jp2 (dommy/text (sel1 current ".ex-jap"))]

                   (if (> (count jp1) (count jp2)) current shortest))) els)
       (sel1 selector)))))

(defn extract-example-japanese [el]
  (let [el (get-shortest-example el ".ex-jap")]
    (if (nil? el) "" (dommy/text el))))

(defn extract-example-english [el]
  (let [el (get-shortest-example el ".ex-en")]
    (if (nil? el) "" (dommy/text el))))

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

(defn pageLoad
  []

  (let [entries (sel ".entry")]
    (doseq [entry entries]
      (let [english-translations (sel entry ".eng")
            kana (extract-kana entry) ;; extract kana
            kanji (extract-kanji entry)] ;; extract kanji
        (doseq [english-el english-translations]
          (let [parent (dommy/parent english-el)
                english-text (dommy/text english-el)

                example-english (extract-example-english parent) ;; extract english example
                example-japanese (extract-example-japanese parent)] ;; extract japanese example
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
