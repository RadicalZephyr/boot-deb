(def project 'radicalzephyr/boot-dpkg)
(def version "0.1.0-SNAPSHOT")

(set-env! :resource-paths #{"src"}
          :dependencies   '[[org.clojure/clojure "1.9.0"]
                            [boot/core "2.7.2" :scope "test"]
                            [metosin/boot-alt-test "0.3.3" :scope "test"]])

(task-options!
 pom {:project     project
      :version     version
      :description "A Boot task for generating debian binary packages"
      :url         "https://github.com/RadicalZephyr/boot-deb"
      :scm         {:url "https://github.com/RadicalZephyr/boot-deb.git"}
      :license     {"Eclipse Public License"
                    "http://www.eclipse.org/legal/epl-v10.html"}})

(deftask build
  "Build and install the project locally."
  []
  (comp (pom) (jar) (install)))

(require '[adzerk.boot-test :refer [test]]
         '[radicalzephyr.boot-dpkg :refer [dpkg]])
