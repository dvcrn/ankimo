(ns ankimo.x.worker.browser
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :as async :refer [chan <! >! close!]]))

(def channels (atom {}))

(defn generate-channel-id
  "Generate a random string to be used as channel-id"
  ([]
   (generate-channel-id (map char (range 49 127))))
  ([alphabet]
   (apply str (take 10 (repeatedly #(rand-nth alphabet))))))

(defn dispatch-message [name payload]
  "Dispatches a message to the background page.
   Returns a channel that will contain the response from background."
  (let [channel-id (generate-channel-id)
        chan (chan)]

    (swap! channels assoc channel-id chan)

    (.dispatchMessage (.. js/safari -self -tab) name (clj->js {:payload payload
                                                               :channel-id channel-id}))
    chan))

(defn message-handler [event]
  "A event message always has to come in the form {:payload ... :channel-id ...}.
   The channel-id gets created in dispatch-message and maps to the channel the answer should be put in"
  (let [event-name (.-name event)
        message (js->clj (.-message event) :keywordize-keys true)
        {payload :payload
         channel-id :channel-id} message
        channel (get @channels channel-id)] ;; fetch channel to answer to

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
