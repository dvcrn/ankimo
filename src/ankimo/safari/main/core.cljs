(ns ankimo.safari.main.core)

(def application (.-application js/safari))

(defmulti handle-message (fn [name payload] (keyword name)))
(defmethod handle-message :default [name payload] (println "no event handler for this message"))

(defmethod handle-message :get-settings [name payload]
  (aget js/safari "extension" "settings" payload))

(defmethod handle-message :get-hoge [name payload]
  (println "get hoge"))

(defn message-handler [event]
  (.log js/console event)
  (let [event-name (.-name event)
        message (js->clj (.-message event) :keywordize-keys true)
        {payload :payload
         channel-id :channel-id} message]

    (let [response (handle-message event-name payload)]
      (.dispatchMessage (.. event -target -page) event-name (clj->js {:channel-id channel-id
                                                                      :payload response})))))

(.addEventListener application "message" message-handler false)

