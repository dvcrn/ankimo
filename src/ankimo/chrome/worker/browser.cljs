(ns ankimo.x.worker.browser
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :as async :refer [chan <! >! close!]]
            [ankimo.chrome.options.config :refer [config-map]]))

(defn get-setting [field]
  (let [c (chan)
        default (get config-map (keyword field) field)]

    (.get (.. js/chrome -storage -sync)
          ;; convert into {field "field"} map, meaning the key is also the default value
          (clj->js (hash-map field default))
          (fn [result]
            (go
              (>! c (aget result field)))))
    c))
