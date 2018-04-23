(ns ankimo.safari.main.core)

(def application (.-application js/safari))

(defmulti handle-message (fn [name payload] (keyword name))) ;; dispatch multi based on keywordized event-name
(defmethod handle-message :default [name payload] (println "no event handler for this message"))

(defmethod handle-message :get-settings [name payload]
  (aget js/safari "extension" "settings" payload))

(defn message-handler [event]
  "A event message always has to come in the form {:payload ... :channel-id ...}.
   The channel-id is used to resolve to the correct core.async channel on the frontend."
  (let [event-name (.-name event)
        message (js->clj (.-message event) :keywordize-keys true)
        {payload :payload
         channel-id :channel-id} message]

    (let [response (handle-message event-name payload)]
      (.dispatchMessage (.. event -target -page) event-name (clj->js {:channel-id channel-id
                                                                      :payload response})))))

(.addEventListener application "message" message-handler false)

