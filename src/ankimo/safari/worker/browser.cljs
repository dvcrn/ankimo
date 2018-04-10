(ns ankimo.x.worker.browser
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :as async :refer [chan <! >! close!]]))

(def channels (atom {}))

(defn generate-channel-id
  ([]
   (generate-channel-id (map char (range 49 127))))
  ([alphabet]
   (apply str (take 10 (repeatedly #(rand-nth alphabet))))))

(defn dispatch-message [name payload]
  (let [channel-id (generate-channel-id)
        chan (chan)]

    (swap! channels assoc channel-id chan)

    (.dispatchMessage (.. js/safari -self -tab) name (clj->js {:payload payload
                                                               :channel-id channel-id}))
    chan))

(defn message-handler [event]
  (let [event-name (.-name event)
        message (js->clj (.-message event) :keywordize-keys true)
        {payload :payload
         channel-id :channel-id} message
        channel (get @channels channel-id)] ;; channel to answer to

    ;; put answer into channel
    (go
      (>! channel payload)

      ;; close channel
      (close! channel)

      ;; remove from registry
      (swap! channels dissoc channel-id))))

(.addEventListener (.. js/safari -self) "message" message-handler false)

(defn get-setting [field]
  (dispatch-message "get-settings" field))

(defn hoge []
  (println "i'm safari still")
  (.log js/console js/safari)
  (.log js/console (.. js/safari -self))
  (.log js/console (.. js/safari -self -tab))
  ;;(.log js/console (.dispatchMessage (.. js/safari -self -tab) "get-settings" "field_kana"))
  (go
    (let [response (<! (dispatch-message "get-settings" "field_kana"))]
      (.log js/console "got answer backk!!!!")
      (.log js/console response))))
