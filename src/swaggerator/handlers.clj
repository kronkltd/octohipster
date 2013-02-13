(ns swaggerator.handlers
  (:use [swaggerator json link util]))

(def ^:dynamic *handled-content-types* (atom []))

(defn wrap-handler-json [handler]
  (swap! *handled-content-types* conj "application/json")
  (fn [ctx]
    (case (-> ctx :representation :media-type)
      "application/json" (let [result (handler ctx)
                               k (:data-key result)]
                           (-> result k jsonify))
      (handler ctx))))

; hal is implemented through a ring middleware
; because it needs to capture links that are not from liberator
(defn hal-links [rsp]
  (into {}
    (concatv
      (map (fn [x] [(:rel x) (-> x (dissoc :rel))]) (:links rsp))
      (map (fn [x] [(:rel x) (-> x (dissoc :rel) (assoc :templated true))]) (:link-templates rsp)))))

(defn wrap-handler-hal-json [handler]
  (swap! *handled-content-types* conj "application/hal+json")
  (fn [ctx]
    (case (-> ctx :representation :media-type)
      "application/hal+json" (let [result (-> ctx handler)
                                   k (:data-key result)
                                   result (k result)
                                   result (if (map? result) result {:_embedded {k result}})]
                               {:_hal result})
      (handler ctx))))

(defn wrap-hal-json [handler]
  (fn [req]
    (let [rsp (handler req)]
      (if-let [hal (:_hal rsp)]
        (-> rsp
            (assoc :body
                   (-> hal
                       (assoc :_links (hal-links rsp))
                       jsonify))
            (dissoc :link-templates)
            (dissoc :links)
            (dissoc :_hal))
        rsp))))
; /hal

(defn wrap-handler-link [handler]
  (fn [ctx]
    (let [result (handler ctx)]
      (if (map? result)
        (-> result
            (assoc :links (:links ctx))
            (assoc :link-templates (:link-templates ctx)))
        {:body result
         :links (:links ctx)
         :link-templates (:link-templates ctx)}))))

(defn wrap-default-handler [handler]
  (-> handler
      wrap-handler-hal-json
      wrap-handler-json
      wrap-handler-link ; last!!
      ))

(defn list-handler
  ([presenter] (list-handler presenter :data))
  ([presenter k]
   (fn [ctx]
     (-> ctx
         (assoc :data-key k)
         (assoc k (mapv presenter (k ctx)))))))

(defn default-list-handler
  ([presenter] (default-list-handler presenter :data))
  ([presenter k] (-> (list-handler presenter k)
                     wrap-default-handler)))

(defn entry-handler
  ([presenter] (entry-handler presenter :data))
  ([presenter k]
   (fn [ctx]
     (-> ctx
         (assoc :data-key k)
         (assoc k (presenter (k ctx)))))))

(defn default-entry-handler
  ([presenter] (default-entry-handler presenter :data))
  ([presenter k] (-> (entry-handler presenter k)
                     wrap-default-handler)))
