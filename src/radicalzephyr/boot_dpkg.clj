(ns radicalzephyr.boot-dpkg
  {:boot/export-tasks true}
  (:require [boot.core :as core]
            [boot.util :as util]
            [clojure.java.io :as io]
            [clojure.string :as str]))


(defn- format-key [k]
  (let [key-words (-> k name (str/split #"-"))]
    (str/join "-" (map str/capitalize key-words))))

(defn- calculate-size [fileset]
  (let [bytes (reduce (fn [bytes tmp-file]
                        (+ bytes (.length (core/tmp-file tmp-file))))
                      0
                      (core/output-files fileset))]
    (-> bytes
        (/ 1024)
        Math/ceil
        int
        (str " kB"))))

(defn- control-file-content [fileset package-details]
  (let [package-details (assoc package-details :installed-size (calculate-size fileset))]
    (str
     (str/join "\n"
               (keep (fn [key]
                       (when (contains? package-details key)
                         (format "%s: %s" (format-key key) (get package-details key))))
                     [:package :version :section :priority
                      :architecture :depends :suggests
                      :conflicts :replaces :installed-size
                      :maintainer :description]))
     "\n")))

(defn- write-md5sums-file [fileset md5sums-file]
  (with-open [f (io/writer md5sums-file)]
    (binding [*out* f]
      (doseq [out-file (core/output-files fileset)]
        (printf "%s  %s\n" (:hash out-file) (:path out-file))))))

(core/deftask dpkg
  "Create the basic structure of a debian package."
  [p package PACKAGE str "The name of the package"
   v version VERSION str "The version number of the package"
   s section SECTION str "The section the package should be in"
   r priority PRIORITY str "The priority of the package"
   a architecture ARCH str "The architecture of the package"
   d depends DEPENDS [str] "Dependencies of the package"
   m maintainer MAINTAINER str "The name of the package maintainer"
   c description DESCRIPTION str "The description of the package"]
  (if (and package version)
    (let [tmp (core/tmp-dir!)
          deb-control-file (io/file tmp "DEBIAN/control")
          deb-md5sums-file (io/file tmp "DEBIAN/md5sums")
          deb-file-name (format "%s_%s_%s.deb" package version architecture)
          deb-tmp (core/tmp-dir!)
          deb-file (io/file deb-tmp deb-file-name)]
      (core/with-pre-wrap [fileset]
        (core/empty-dir! tmp)
        (io/make-parents deb-control-file)
        (util/info "Writing debian package control files...\n")
        (spit deb-control-file (control-file-content fileset *opts*))
        (write-md5sums-file fileset deb-md5sums-file)
        (doseq [tmp-file (core/ls fileset)]
          (let [new-copy (io/file tmp (core/tmp-path tmp-file))]
            (io/make-parents new-copy)
            (io/copy (core/tmp-file tmp-file) new-copy)))
        (core/empty-dir! deb-tmp)
        (util/dosh "dpkg-deb" "--build" (.getAbsolutePath tmp) (.getAbsolutePath deb-file))
        (-> fileset
            (core/add-resource tmp)
            (core/add-resource deb-tmp)
            core/commit!)))
    (util/warn "Must specify package name and version.")))
