(ns ankibuddy.worker.common.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
   [dommy.core :as dommy :refer-macros [sel sel1]]
   [anki-cljs.core :as anki]
   [cljs.core.async :as async :refer [<! >!]]
   [cljs.core.match :refer-macros [match]]
   [ankibuddy.browser :as browser]))

(defn create-button [text handler]
    ;; create
  (let [button (->
                (dommy/create-element "a")
                (dommy/add-class! :ankibuddy-add)
                (dommy/set-text! text))]
    (dommy/listen! button :click handler)))

(defn extract-text
  [el selector]
  (->> (map dommy/text (sel el selector))
       (clojure.string/join ", ")))

(defn extract-kana [el] (extract-text el ".kana ruby rb"))
(defn extract-kanji [el] (extract-text el ".writing"))

(defn add-to-anki [kanji kana english]
  (go
    (let [field-kanji (<! (browser/get-setting "field_kanji"))
          field-kana (<! (browser/get-setting "field_kana"))
          field-english (<! (browser/get-setting "field_english"))
          deck-name (<! (browser/get-setting "deck_name"))
          model-name (<! (browser/get-setting "model_name"))]

      (let [response (<! (anki/add-note deck-name model-name {:kanji kanji
                                                              :hiragana kana
                                                              :english english}
                                        [:ankibuddy :tangorin]))]
        (match response
          [:ok _] (.alert js/window "Added to Anki!")
          [:error msg] (.alert js/window msg))))))

(defn pageLoad
  []

  (let [entries (sel ".entry")]
    (doseq [entry entries]
      (let [english-translations (sel entry ".eng")
            kana (extract-kana entry) ;; extract kana
            kanji (extract-kanji entry)] ;; extract kanji
        (doseq [english-el english-translations]
          (let [english-text (dommy/text english-el)]
            (->> (create-button "add to anki" #(add-to-anki kanji kana english-text))
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
