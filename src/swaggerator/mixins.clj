(ns swaggerator.mixins
  (:require [liberator.core :as lib])
  (:use [swaggerator pagination validator util]
        [swaggerator.handlers core json edn yaml hal cj util]
        [swaggerator.link util]
        [swiss-arrows core]))

(defn validated-resource [r]
  (update-in r [:middleware] conj #(wrap-json-schema-validator % (:schema r))))

(defn handled-resource
  ([r] (handled-resource r item-handler))
  ([r handler] (handled-resource r handler nil nil))
  ([r handler rel-key clink-templated]
   (let [r (merge {:handlers [wrap-handler-json wrap-handler-edn wrap-handler-yaml
                              wrap-handler-hal-json wrap-handler-collection-json]
                   :data-key :data
                   :presenter identity}
                  r)
         cl-wrapper (if rel-key #(wrap-handler-add-clink % (rel-key r) clink-templated) identity)
         h (-<> (handler (:presenter r) (:data-key r))
                (reduce #(%2 %1) <> (:handlers r))
                cl-wrapper
                wrap-default-handler)]
     (-> r
         (assoc :handle-ok h)
         (assoc :available-media-types
                (apply concat (map (comp :ctypes meta) (:handlers r))))))))

(defn item-resource
  "Mixin that includes all boilerplate for working with single items:
   - validation (using JSON schema in :schema for PUT requests)
   - format handling
   - linking to the item's collection"
  [r]
  (let [r (merge {:method-allowed? (lib/request-method-in :get :put :delete)
                  :collection-key :collection
                  :respond-with-entity? true
                  :new? false
                  :can-put-to-missing? false}
                 r)]
    (-> r
        (update-in [:clinks] assoc :collection (:link-to-collection r))
        validated-resource
        (handled-resource item-handler :collection-key false))))

(defn collection-resource
  "Mixin that includes all boilerplate for working with collections of items:
   - validation (using JSON schema in :schema for POST requests)
   - format handling
   - linking to the individual items
   - pagination"
  [r]
  (let [r (merge {:method-allowed? (lib/request-method-in :get :post)
                  :data-key :data
                  :item-key :item
                  :post-redirect? true
                  :default-per-page 25}
                 r)]
    (-> r
        (assoc :see-other (params-rel (:item-key r)))
        (update-in [:clinks] assoc :item (:link-to-item r))
        (update-in [:middleware] conj
                   #(wrap-pagination % {:counter (:count r)
                                        :default-per-page (:default-per-page r)}))
        validated-resource
        (handled-resource collection-handler :item-key true))))