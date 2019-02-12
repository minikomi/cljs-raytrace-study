(ns raytrace.core
  (:require
   [reagent.core :as reagent]
   [goog.object :as gobj]
   [thi.ng.geom.core :as g]
   [thi.ng.geom.vector :as v :refer [vec2 vec3 V3Y V3Z]]
   [thi.ng.math.core :as m ]
   [thi.ng.color.core :as col]
   ))

(enable-console-print!)


(def W 1024)
(def H 768)
(def FOV (/ Math/PI 2))
(def BACKGROUND_COLOR (col/rgba 0.2 0.4 0.3 1))


(def half-screen-width (Math/tan (/ FOV 2)))
(def aspect-ratio( / W H))

(def buffer (js/Uint8ClampedArray. (* W H 4)))
(def imgbuffer (js/ImageData. buffer W))
(gobj/set imgbuffer "data" buffer)

(def light
  {:position (vec3 -5 10 10)
   :intensity 3.9})

(defn create-sphere [center radius color]
  {:center center
   :r radius
   :color color
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
         (= radius |center-to-origin|)
         {:di origin
          :dist_di radius}
         ;; sphere is behind and origin is outside
         (and behind
              (< radius |center-to-origin|)) false
         ;; sphere is behind but intersects
         behind
         (let [|intersection-to-origin|
               (- |intersection-to-projection|
                  |projection-to-center|)]
           {:di (m/+ origin (m/* dir |intersection-to-origin|))
            :dist_di |intersection-to-origin|})
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
           {:di (m/* dir |intersection-to-origin|)
            :dist_di |intersection-to-origin|}))))})

(defn set-pixel [x y [r g b _]]
  (let [offset (* 4 (+  x (* y W)))]
    (aset buffer offset (* r 255))
    (aset buffer (+ offset 1) (* g 255))
    ( aset buffer (+ offset 2) (* b 255))
    (aset buffer (+ offset 3) 255)))

(defn create-image [ctx origin {:keys [center intersect? color]} t]
  (doseq [y (range H)]
    (doseq [x (range W)]
      (let [ray-x-point (+ x 0.5)
            ray-y-point (+ y 0.5)
            ray-x-ratio (/ ray-x-point (/ W 2))
            ray-y-ratio (/ ray-y-point (/ H 2))
            dx (* (dec ray-x-ratio)
                  half-screen-width
                  aspect-ratio)
            dy (- (* (dec ray-y-ratio)
                     half-screen-width))]
        (if-let [{:keys [di dist_di]} (intersect? origin (m/normalize (vec3 dx dy -1)))]
          (let [light-dir (m/normalize (m/- (:position light) di))
                hit (m/+ origin di)
                normal (m/normalize (m/- center hit))
                light-intensity (m/* (vec3 (:intensity light))
                                     (m/* light-dir normal))]
            (set-pixel x y (m/* color light-intensity)))
          (set-pixel x y @BACKGROUND_COLOR)))))
  (.putImageData ctx imgbuffer 0 0))



(defn update-raytrace [ctx t fid]
  (let [s (create-sphere (vec3 -3 0 -8) 2 (vec3 1 0.5 0.25))
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
