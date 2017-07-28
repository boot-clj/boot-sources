(ns powerlaces.boot-sources
  {:boot/export-tasks true}
  (:require [clojure.string :as string]
            [boot.core :as core :refer [boot]]
            [boot.pod :as pod]
            [boot.from.digest :as digest]
            [boot.util :as util]
            [boot.task.built-in :as built-in]))

;; https://github.com/boot-clj/boot/pull/398
(defn filter-tmpfiles
  "Filter TmpFiles that match (a least one in) metadata-preds.

  Metadata-preds is a set of functions which will receive a TmpFile and
  tried not in a specific order. If no predicate in the set returns
  true-y, the file is filtered out from the returned TmpFiles.

  If invert is specified, the behavior is reversed."
  ([tmpfiles metadata-preds]
   (filter-tmpfiles tmpfiles metadata-preds false))
  ([tmpfiles metadata-preds invert?]
   (when (seq metadata-preds)
     ((if-not invert? filter remove) (apply some-fn metadata-preds) tmpfiles))))

(defn normalize-path
  "Adds a / if missing at the end of the path."
  [path]
  (str path (when-not (= "/" (last path)) "/")))

(core/deftask rebase
  "A task for moving TmpFile(s) from one dir to another in the fileset.

  Note that the destination will be magically materialized.

  For instance, if you want to rebase all the files with no ::initial-fileset
  metadata to my-dest-dir:

  (boot (rebase :path \"my-dest-dir\"
                :with-meta #{::initial-fileset}
                :invert true)"
  [w with-meta   KEY  #{kw} "The set of metadata keys files must have for being rebased."
   d destination PATH str   "The destination path to rebase the filtered fileset into."
   v invert      bool       "Invert the sense of with-meta."]

  (core/with-pre-wrap fileset
    (let [files (filter-tmpfiles (core/ls fileset) with-meta invert)
          paths (map (juxt :path #(str (normalize-path destination) (:path %))) files)]
      (core/commit! (reduce (fn [acc [from-path to-path]]
                              (merge acc (core/mv acc from-path to-path))) fileset paths)))))

(core/deftask sift-jars
  "Custom version of boot.task.built-in/sift :add-jar which accepts
  a (previously resolved) jar file in input."
  [j jars    PATH  #{str}   "The paths of the jar files."
   i include MATCH #{regex} "The set of regexes that paths must match."
   e exclude MATCH #{regex} "The set of regexes that paths must NOT match."]
  (core/with-pre-wrap fileset
    (reduce (fn [fs jar]
              (-> fs
                  (core/add-cached-resource
                   (digest/md5 jar)
                   (partial pod/unpack-jar jar)
                   :include include
                   :exclude exclude
                   :mergers pod/standard-jar-mergers)
                  core/commit!))
            fileset
            jars)))

(def default-scopes #{"compile"})

(defn correct-scope?
  [dep-map]
  (contains? default-scopes (:scope dep-map)))

(defn clojurescript?
  [dep-map]
  (= 'org.clojure/clojurescript (:project dep-map)))

(defn include-dependency?
  [dep-map]
  (or (clojurescript? dep-map) (correct-scope? dep-map)))

(defn map-as-dep
  "Returns the given dependency vector with :project and :version put at
  index 0 and 1 respectively and modifiers (eg. :scope, :exclusions,
  etc) next."
  [{:keys [project version] :as dep-map}]
  (let [kvs (remove #(or (some #{:project :version} %)
                         (= [:scope "compile"] %)) dep-map)]
    (vec (remove nil? (into [project version] (flatten kvs))))))

(def ^:private inclusion-regex-set
  "Entries matching these Patterns will be included."
  #{#".clj$"
    #".cljs$"
    #".cljc$"
    #".js$"})

(def ^:private exclusion-regex-set
  "Entries matching these Patterns will not be included."
  #{#"project.clj"})

(core/deftask pack-sources
  "Add the relevant source files from the project dependencies.

  Specifically, this task moves all the clj, cljs, cljc and js files to the
  to-dir folder specified by the user (defaulting to clj-src) and keeping
  intact the original namespace structure.

  Currently only the \"compile\" scope is taken into consideration and in case
  no :dependencies is present, the task will use (:dependencies (get-env))

  The default inclusion set is #{#\".clj$\" #\".cljs$\" #\".cljc$\" #\".js$\"
  whereas the default exclusion set is #{#\"project.clj\"}. If you provide the
  include or exclude regex sets, the defaults will be replaced, not merged.

  Exclusions is a set of symbols which will completely discard a dependency,
  transitive dependencies included."
  [t to-dir       DIR     str          "The dir to materialize source files into."
   d dependencies SYM:VER #{[sym str]} "The dependency vector to pack."
   x exclusions   DEP     #{sym}       "The dependency symbol to exclude explicitly."
   i include      MATCH   #{regex}     "The set of regexes that paths must match."
   e exclude      MATCH   #{regex}     "The set of regexes that paths must NOT match."]
  (let [dest-dir (or to-dir "clj-src")
        env (update (core/get-env) :dependencies
                    (fn [old-deps]
                      (->> (vec (or dependencies old-deps))
                           (map util/dep-as-map)
                           (filter include-dependency?)
                           (map map-as-dep)
                           (remove #(contains? (or exclusions #{}) (first %)))
                           vec)))
        include-set (or include inclusion-regex-set)
        exclude-set (or exclude exclusion-regex-set)
        jars (->> (pod/resolve-dependencies env)
                  (remove #(contains? (or exclusions #{}) (-> % :dep first)))
                  (map :jar))]
    (util/dbug "Including source from the following jars:\n%s\n" (string/join "\n" jars))
    (comp (built-in/sift :add-meta {#".*" ::initial-fileset})
          (sift-jars :jars jars
                     :include include-set :exclude exclude-set)
          (rebase :destination dest-dir
                  :with-meta #{::initial-fileset} :invert true))))


(comment
  (reset! util/*verbosity* 0)
  (boot (pack-sources :dependencies #{['org.clojure/clojurescript "1.8.34"]}) (built-in/show :fileset true))

  (boot (pack-sources :dependencies #{['org.clojure/clojurescript "1.8.34"]}
                     :exclude #{#"project.clj"
                                #"third_party\/closure\/.*base.js$"
                                #"third_party\/closure\/.*deps.js$"}
                     :exclusions '#{org.clojure/clojure
                                    org.mozilla/rhino})
        (built-in/target))

  (def tmp (core/tmp-dir!))
  (def tmp-str (.tmp))
  (def jar "/home/kapitan/.m2/repository/org/mozilla/rhino/1.7R5/rhino-1.7R5.jar")
  (sift-jar :jar jar :include inclusion-regex)

  (def closure-files (boot.core/by-re [#"closure\/goog\/.*.js$"
                                       #"third_party\/closure\/goog\/.*.js$"] files))
  (def base-deps-files (boot.core/not-by-re [#"third_party\/closure\/.*base.js$"
                                             #"third_party\/closure\/.*deps.js$"] closure-files))

  (util/purge-dir! tmp))
