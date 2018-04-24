(ns ankimo.chrome.options.core
  (:require [ankimo.chrome.options.config :refer [config-map]]))

(defn get-element-value-by-id [id]
  (->
   (.getElementById js/document id)
   (aget "value")))

(defn set-element-value-by-id [id value]
  (->
   (.getElementById js/document id)
   (aset "value" value)))

(defn load-settings []
  ;; retrieve settings from local storage
  (.get (.. js/chrome -storage -sync) (clj->js config-map)
        (fn [items]
          ;; iterate over all option fields and update the DOM to the returned value
          (doseq [field (keys config-map)]
            (let [field-str (name field)]
              (set-element-value-by-id field-str (aget items field-str)))))))

(defn save-settings []
  ;; retrieve all the current option values with getElementById, merge them into a map like {:field-kanji "kanji"}
  (let [new-options (->> (keys config-map)
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
