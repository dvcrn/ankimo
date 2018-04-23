(ns ankimo.x.worker.browser
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :as async :refer [chan <! >! close!]]))

(defn get-setting [field]
  (let [c (chan)]
    (.get (.. js/chrome -storage -sync)
          ;; convert into {field "field"} map, meaning the key is also the default value
          (clj->js (hash-map field field))
          (fn [result]
            (go
              (>! c (aget result field)))))
    c))
