(ns ankimo.chrome.options.core)

(defn get-element-value-by-id [id]
  (->
   (.getElementById js/document id)
   (aget "value")))

(defn set-element-value-by-id [id value]
  (->
   (.getElementById js/document id)
   (aset "value" value)))

(def option-fields [:deck_name :model_name :field_kanji :field_kana :field_english])

(defn load-settings []
  ;; convert all option fields into a map in form of {:key "key"}
  (def option-map
    (->> option-fields
         (map #(hash-map %1 (name %1)))
         (apply merge)))

  ;; retrieve settings from local storage
  (.get (.. js/chrome -storage -sync) (clj->js option-map)
        (fn [items]
          ;; iterate over all option fields and update the DOM to the returned value
          (doseq [field option-fields]
            (let [field-str (name field)]
              (set-element-value-by-id field-str (aget items field-str)))))))

(defn save-settings []
  ;; retrieve all the current option values with getElementById, merge them into a map like {:field-kanji "kanji"}
  (let [new-options (->> option-fields
                         (map #(hash-map %1 (get-element-value-by-id (name %1))))
                         (apply merge))]
    ;; update chrome local storage with new options
    (.set (.. js/chrome -storage -sync) (clj->js new-options)
          ;; callback, visual notification for the user
          (fn []
            (aset (.getElementById js/document "status") "textContent" "Saved!")
            (.setTimeout js/window #(aset (.getElementById js/document "status") "textContent" "") 750)))))

(.addEventListener (.getElementById js/document "save") "click" save-settings)
(.addEventListener js/document "DOMContentLoaded" load-settings)
