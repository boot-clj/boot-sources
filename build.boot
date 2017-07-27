(set-env!
 :source-paths #{"src"}
 :dependencies '[[degree9/boot-semver "1.6.0" :scope "test"]])

(require '[boot-semver.core :refer :all])

(task-options! pom {:project 'powerlaces/boot-sources
                    :description "Boot task that collects and stores Clojure(Script) source files."
                    :url "https://github.com/boot-clj/boot-sources"
                    :scm {:url "https://github.com/boot-clj/boot-sources.git"}
                    :license {"Eclipse Public License" "http://www.eclipse.org/legal/epl-v10.html"}})


(ns-unmap 'boot.user 'test)

;; -- My Tasks --------------------------------------------

(deftask deploy
  "Build boot-sources and deploy to clojars."
  []
  (comp
   (version)
   (target)
   (build-jar)
   (push-release)))

(deftask set-dev! []
  (set-env! :source-paths #(conj % "test")
            :dependencies #(into % '[[adzerk/boot-test "1.1.1" :scope "test"]]))
  (require 'adzerk.boot-test))

(deftask test
  "Testing once (dev profile)"
  []
  (set-dev!)
  (comp ((eval 'adzerk.boot-test/test))))

(deftask auto-test
  "Start auto testing mode (dev profile)"
  []
  (set-dev!)
  (comp (watch)
        (speak)
        ((eval 'adzerk.boot-test/test))))
