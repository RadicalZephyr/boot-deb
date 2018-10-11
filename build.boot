(def project 'radicalzephyr/boot-dpkg)
(def version "0.2.0")

(set-env! :resource-paths #{"src"})

(task-options!
 pom {:project     project
      :version     version
      :description "A Boot task for generating debian binary packages"
      :url         "https://github.com/RadicalZephyr/boot-dpkg"
      :scm         {:url "https://github.com/RadicalZephyr/boot-dpkg"}
      :license     {"Eclipse Public License"
                    "http://www.eclipse.org/legal/epl-v10.html"}})

(deftask build
  "Build and install the project locally."
  []
  (comp (pom) (jar) (install)))
