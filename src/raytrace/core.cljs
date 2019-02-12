(ns raytrace.core
  (:require
   [reagent.core :as reagent]
   [goog.object :as gobj]
   [thi.ng.geom.core :as g]
   [thi.ng.geom.vector :as v :refer [vec2 vec3 V3Y V3Z]]
   [thi.ng.math.core :as m ]
   ))

(enable-console-print!)


(def W 1024)
(def H 768)
(def FOV (/ Math/PI 2))

(def buffer (js/Uint8ClampedArray. (* W H 4)))
(def imgbuffer (js/ImageData. buffer W))
(gobj/set imgbuffer "data" buffer)

(defn set-pixel [x y [r g b]]
  (let [offset (* 4 (+  x (* y W)))]
    (aset buffer offset r)
    (aset buffer (+ offset 1) g)
    (aset buffer (+ offset 2) b)
    (aset buffer (+ offset 3) 255)))

(defn create-image [ctx origin {:keys [intersect?]} t]
  (doseq [y (range H)]
    (doseq [x (range W)]
      (let [dx (* (/ (* 2 (+ x 0.5)) W)
                  (/ (Math/tan (/ FOV 2)))
                  (/ W H))
            dy (* (- (/ (* 2 (+ y 0.5)) H))
                  (Math/tan (/ FOV 2)))]
        (if (boolean (intersect? origin (m/normalize (vec3 dx dy -1))))
          (set-pixel x y [20 20 20])
          (set-pixel x y [120 255 255])))))
  (.putImageData ctx imgbuffer 0 0))

(defn create-sphere [center radius]
  {:center center
   :r radius
   :intersect?
   (fn [origin dir]
     (let [center-to-origin
           (m/- center origin)
           oc-dot-dir
           (m/dot center-to-origin dir)
           |center-to-origin|
           (m/mag center-to-origin)
           projection-center-on-ray
           (m/* dir oc-dot-dir)
           |projection-to-center|
           (m/mag (m/- projection-center-on-ray center))
           |intersection-to-projection|
           (Math/sqrt
            (- (Math/exp radius 2)
               (Math/exp |projection-to-center| 2)))
           behind (neg? oc-dot-dir)]
       (cond
         ;; it intersects at the origin
         (= radius |center-to-origin|) origin
         ;; sphere is behind and origin is outside
         (and behind
              (< radius |center-to-origin|)) false
         ;; sphere is behind but intersects
         behind
         (let [|intersection-to-origin|
               (- |intersection-to-projection|
                  |projection-to-center|)]
           (m/+ origin (m/* dir |intersection-to-origin|)))
         ;; sphere is too far from ray
         (< radius |projection-to-center|) false
         ;; sphere is in front, origin outside sphere
         :else
         (let [|intersection-to-origin|
               (if (< radius |center-to-origin|)
                 (- |projection-to-center|
                    |intersection-to-projection|)
                 (+ |projection-to-center|
                    |intersection-to-projection|))]
           (m/* dir |intersection-to-origin|)))))})

(defn update-raytrace [ctx t fid]
  (let [s (create-sphere (vec3 20 -10 -16) 4)
        origin (vec3 0 0 0)]
    (create-image ctx origin s t)))

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
              t0 (.getTime (js/Date.))
              fid (volatile! 0)
              loop-fn (fn loop-fn []
                        (vswap! fid inc)
                        (update ctx
                                (* (- (.getTime (js/Date.)) t0) 1e-3)
                                (vswap! fid inc))
                        (and @dom-node (js/requestAnimationFrame loop-fn)))
              dpr (or (.-devicePixelRatio js/window) 1)]
          (.setAttribute canvas "width" (* dpr client-w))
          (.setAttribute canvas "height" (* dpr client-h))
          (set! (.. canvas -style -width) client-w)
          (set! (.. canvas -style -height) client-h)
          (.scale ctx dpr dpr)
          ;; (loop-fn)
          (update-raytrace ctx 0 0)
          ))
      :component-will-unmount
      (fn [this]
        (reset! dom-node false))
      :reagent-render
      (fn [offset timezone-label]
        [:div#canvas
         {:style {:width W :height H}}
         [:canvas {:width "100%" :height "100%"}]])})))

(defn init []
  (reagent/render [game-looper #'update-raytrace (atom {})]
                  (.getElementById js/document "app")))
