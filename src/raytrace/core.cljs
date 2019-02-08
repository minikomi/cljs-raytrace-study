(ns raytrace.core
  (:require
   [reagent.core :as reagent]
   [goog.object :as gobj]
   ))

(enable-console-print!)


(def W 1024)
(def H 768)

(def buffer (js/Uint8ClampedArray. (* W H 4)))

(defn set-pixel [w h [r g b]]
  (let [offset (* 4 (+  w (* h W)))]
    (aset buffer offset r)
    (aset buffer (+ offset 1) g)
    (aset buffer (+ offset 2) b)
    (aset buffer (+ offset 3) 255)))

(defn create-image [ctx]
  (doseq [y (range H)]
    (doseq [x (range W)]
      (set-pixel x y [(* 255(/ x W)) (* 255 (/ y H)) 0])))
  (.putImageData ctx (js/ImageData. buffer W) 0 0))

(defn update-raytrace [ctx state]
  (create-image ctx))

(defn game-looper [update]
  (let [dom-node (atom nil)]
    (reagent/create-class
     {:component-did-mount
      (fn [this]
        (reset! dom-node (reagent/dom-node this))
        (let [canvas (.-firstChild @dom-node)
              id (rand-int 100)
              client-w (.-clientWidth @dom-node)
              client-h (.-clientHeight @dom-node)
              ctx (.getContext canvas "2d")
              loop-fn (fn loop-fn []
                        (update ctx)
                        (if @dom-node (js/requestAnimationFrame loop-fn)))
              dpr (or (.-devicePixelRatio js/window) 1)]
          (.setAttribute canvas "width" (* dpr client-w))
          (.setAttribute canvas "height" (* dpr client-h))
          (set! (.. canvas -style -width) client-w)
          (set! (.. canvas -style -height) client-h)
          (.scale ctx dpr dpr)
          (loop-fn)))
      :component-will-unmount
      (fn [this]
        (reset! dom-node false))
      :reagent-render
      (fn [offset timezone-label]
        [:div#canvas
         {:style {:width W :height H}}
         [:canvas {:width "100%" :height "100%"}]
         ])})))

(def state (atom {}))

(defn init []
  (reagent/render [game-looper #'update-raytrace state]
                  (.getElementById js/document "app")))
