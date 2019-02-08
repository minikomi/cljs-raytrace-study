(set-env!
 :source-paths #{"src"}
 :resource-paths #{"resources"}
 :target-path "./target/"
 :dependencies '[;; pin deps
                 [org.clojure/clojure "1.10.0" :scope "provided"]
                 [org.clojure/clojurescript "1.10.516"]
                 ;; cljs
                 [adzerk/boot-cljs "2.1.5" :scope "test"]
                 ;; dev
                 [adzerk/boot-cljs-repl "0.4.0"]
                 [adzerk/boot-reload "0.6.0" :scope "test"]
                 [pandeiro/boot-http "0.8.3" :scope "test"]
                 [powerlaces/boot-cljs-devtools "0.2.0" :scope "test"]
                 [ring-logger "1.0.1"]
                 [ring/ring-defaults "0.3.0"]
                 [weasel "0.7.0" :scope "test"]
                 [cider/piggieback "0.3.9" :scope "test"]
                 [nrepl "0.4.5" :scope "test"]
                 ;; Frontend
                 [reagent "0.8.1"]
                 [binaryage/oops "0.6.4"]
                 [cljsjs/pixi "4.7.0-0"]])


(require
 'boot.repl
 '[adzerk.boot-cljs      :refer [cljs]]
 '[adzerk.boot-reload    :refer [reload]]
 '[pandeiro.boot-http    :refer [serve]]
 '[adzerk.boot-cljs-repl :refer [cljs-repl start-repl]])


(deftask build []
  (comp
   (cljs)))

(deftask cider "CIDER profile" []
  (alter-var-root #'clojure.main/repl-requires conj
                  '[sekistone.server.repl :refer [start! stop! restart!]])
  (require 'boot.repl)
  (swap! @(resolve 'boot.repl/*default-dependencies*)
         concat '[[cider/cider-nrepl "0.20.1-SNAPSHOT"]
                  [nrepl "0.5.3"]
                  [refactor-nrepl "2.4.0"]
                  ])
  (swap! @(resolve 'boot.repl/*default-middleware*)
         concat '[cider.nrepl/cider-middleware
                  refactor-nrepl.middleware/wrap-refactor])
  (repl :server true))

(deftask development []
  (task-options! cljs {:optimizations :none
                       :compiler-options {:closure-defines {'goog.DEBUG true}}})
  identity)

(deftask production []
  (task-options! cljs {:optimizations :advanced
                       :source-map false
                       :compiler-options {:elide-asserts true
                                          :pretty-print false
                                          :closure-defines {'goog.DEBUG false}}})
  identity)

(deftask optimized []
  (task-options! cljs {:optimizations :advanced
                       :source-map false
                       :compiler-options {:elide-asserts true
                                          :pretty-print true
                                          :pseudo-names true
                                          :closure-defines {'goog.DEBUG false}}})
  identity)

(deftask dev []
  (comp
   (development)
   (cider)
   (serve)
   (watch)
   (cljs-repl)
   (reload :on-jsload 'raytrace.core/init
           :ws-host "0.0.0.0"
           :asset-path "/public")
   (build)))

(deftask pseudo []
  (comp
   (optimized)
   (cider)
   (serve)
   (watch)
   (reload :on-jsload 'raytrace.core/init
           :ws-host "0.0.0.0"
           :asset-path "/public")
   (build)
   (sift :include #{#"\.out" #"\.cljs\.edn$" #"^\." #"/\."} :invert true)
   (target)))


(deftask prod []
  (comp
   (production)
   (build)
   (sift :include #{#"\.out" #"\.cljs\.edn$" #"^\." #"/\."} :invert true)
   (target)))
