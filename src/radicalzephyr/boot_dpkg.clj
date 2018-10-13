(ns radicalzephyr.boot-dpkg
  {:boot/export-tasks true}
  (:require [boot.core :as core]
            [boot.pod :as pod]
            [boot.util :as util]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import
   (java.io File)
   (java.nio.file CopyOption
                  Files
                  StandardCopyOption)))

(defn- canonicalize-chowns [chowns]
  (reduce (fn [acc [path user-group]]
            (let [[user group] (str/split user-group #":")
                  group (or group user)]
              (if (and user group)
                (assoc acc path [user group])
                acc)))
          {}
          chowns))

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
      (doseq [out-file (core/by-re [#"^DEBIAN"] (core/output-files fileset) true)]
        (printf "%s  %s\n" (:hash out-file) (:path out-file))))))

(def ^:private copy-attributes
  (into-array CopyOption [StandardCopyOption/COPY_ATTRIBUTES]))

(defn- copy! [^File from ^File to]
  (Files/copy (.toPath from)
              (.toPath to)
              copy-attributes))

(defn- write-conffiles-file [fileset conffiles-file conf-files]
  (with-open [f (io/writer conffiles-file)]
    (binding [*out* f]
      (let [etc-files (->> fileset
                           core/output-files
                           (core/by-re [#"^etc/"]))
            other-conf-files (if (seq conf-files)
                               (->> fileset
                                    core/output-files
                                    (core/by-path conf-files))
                               [])]
        (doseq [out-file (concat etc-files other-conf-files)]
          (printf "/%s\n" (:path out-file)))))))

(defn- create-deb-package [worker-pod in-dir out-file chowns]
  (pod/with-call-in worker-pod
    (radicalzephyr.boot-dpkg.archive/create-deb-package
     ~(.getAbsolutePath in-dir)
     ~(.getAbsolutePath out-file)
     ~chowns)))

(core/deftask dpkg
  "Create the basic structure of a debian package.

  Most of the options directly correspond to a field in the `control`
  file and no processing is done on them. The `Installed-Size` is
  calculated automatically.

  The md5sums control file is generated automatically from the entire
  fileset, excluding any files under \"DEBIAN\".

  The conffiles listing is generated automatically, and by default
  includes all files in the filset under the \"etc\" directory.
  Additional configuration files can be specified with the
  --conf-files option as a seq of absolute paths.  These paths MUST
  EXIST in the fileset, or they will not be include in the conffiles
  listing.

  The --chowns option allows specifying a set of ownership changes to
  make. The ROOT portion should be a path relative to the fileset, the
  OWNER portion should be either a simple username or a user and group
  name separated by a colon: `USER:GROUP`. If only the username is
  specified, it will be used for the group name as well.  A recursive
  ownership change is done on each of the specified ROOTs. The intent
  is to essentially be equivalent to running `chown -R OWNER ROOT`."

  [p package PACKAGE str "The name of the package."
   v version VERSION str "The version number of the package."
   s section SECTION str "The section the package."
   r priority PRIORITY str "The priority of the package."
   a architecture ARCH str "The architecture of the package."
   d depends DEPENDS [str] "Dependencies of the package."
   m maintainer MAINTAINER str "The name of the package maintainer."
   c description DESCRIPTION str "The description of the package."
   o chowns ROOT:OWNER {str str} "The mapping of root directories to file owner strings."
   n conf-files PATHS #{str} "Paths to be marked as configuration files."]

  (when-not (and package version)
    (throw (Exception. "need package name and version to create deb package")))
  (let [pod-env (update-in (core/get-env) [:dependencies]
                           conj
                           '[org.apache.commons/commons-compress "1.18"]
                           '[commons-io "2.6"]
                           '[org.tukaani/xz "1.8"])
        worker-pod (pod/make-pod pod-env)
        tmp (core/tmp-dir!)
        architecture (or architecture "all")
        chowns (canonicalize-chowns chowns)
        conf-files (or conf-files #{})
        deb-control-file (io/file tmp "DEBIAN/control")
        deb-md5sums-file (io/file tmp "DEBIAN/md5sums")
        deb-conffiles-file (io/file tmp "DEBIAN/conffiles")
        deb-file-name (format "%s_%s_%s.deb" package version architecture)
        deb-tmp (core/tmp-dir!)
        deb-file (io/file deb-tmp deb-file-name)]
    (core/cleanup (pod/destroy-pod worker-pod))
    (pod/with-eval-in worker-pod
      (require '[radicalzephyr.boot-dpkg.archive]))
    (core/with-pre-wrap [fileset]
      (core/empty-dir! tmp)
      (io/make-parents deb-control-file)
      (util/info "Writing debian package control files...\n")
      (spit deb-control-file (control-file-content fileset *opts*))
      (write-md5sums-file fileset deb-md5sums-file)
      (write-conffiles-file fileset deb-conffiles-file conf-files)
      (doseq [tmp-file (core/ls fileset)]
        (let [new-copy (io/file tmp (core/tmp-path tmp-file))]
          (io/make-parents new-copy)
          (copy! (core/tmp-file tmp-file) new-copy)))
      (core/empty-dir! deb-tmp)
      (create-deb-package worker-pod tmp deb-file chowns)
      (-> fileset
          (core/add-resource tmp)
          (core/add-resource deb-tmp)
          core/commit!))))
