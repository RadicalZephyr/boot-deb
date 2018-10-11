(ns radicalzephyr.boot-dpkg
  {:boot/export-tasks true}
  (:require [boot.core :as core]
            [boot.util :as util]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import
   (java.io File
            IOException)
   (java.nio.file CopyOption
                  Files
                  FileSystems
                  FileVisitResult
                  LinkOption
                  SimpleFileVisitor
                  StandardCopyOption)
   (java.nio.file.attribute PosixFileAttributeView
                            UserPrincipalLookupService)))


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
      (doseq [out-file (core/by-re [#"^DEBIAN"] (core/output-files fileset) true)
              :when (not (re-matches #"^DEBIAN" (core/tmp-path out-file)))]
        (printf "%s  %s\n" (:hash out-file) (:path out-file))))))

(def ^:private copy-attributes
  (into-array CopyOption [StandardCopyOption/COPY_ATTRIBUTES]))

(defn- copy! [^File from ^File to]
  (Files/copy (.toPath from)
              (.toPath to)
              copy-attributes))

(defn- lookup [chowns]
  (let [^UserPrincipalLookupService
        lookup-service (.getUserPrincipalLookupService
                        (FileSystems/getDefault))]
    (reduce (fn [acc [re user-group]]
              (let [[user group] (str/split user-group #":")
                    group (or group user)
                    user (.lookupPrincipalByName lookup-service user)
                    group (.lookupPrincipalByGroupName lookup-service group)]
                (if (and user group)
                  (assoc acc re [user group])
                  acc)))
            {}
            chowns)))

(defn- chown-path [root-path path user group]
  (try
    (Files/setOwner path user)
    (catch IOException e
      (util/fail "Could not change owner of %s.\n"
                 (str/replace (.getMessage e) (str root-path "/") ""))))
  (try
    (-> path
        (Files/getFileAttributeView
         PosixFileAttributeView
         (into-array LinkOption [LinkOption/NOFOLLOW_LINKS]))
        (.setGroup group))
    (catch IOException e
      (util/fail "Could not change group of %s\n"
                 (str/replace (.getMessage e) (str root-path "/") "")))))

(defn- mk-chown-visitor [root-dir user group]
  (let [root-path (.toPath root-dir)]
    (proxy [SimpleFileVisitor]
        []
      (postVisitDirectory [dir exc]
        (chown-path root-path dir user group)
        FileVisitResult/CONTINUE)

      (visitFile [file attrs]
        (chown-path root-path file user group)
        FileVisitResult/CONTINUE))))

(defn- change-ownership [root-dir chowns]
  (doseq [[chown-root [user group]] chowns
          :let [root-path (.toPath (io/file root-dir chown-root))]]
    (Files/walkFileTree root-path (mk-chown-visitor root-dir user group))))

(core/deftask dpkg
  "Create the basic structure of a debian package.

  Most of the options directly correspond to a field in the `control`
  file and no processing is done on them. The `Installed-Size` is
  calculated automatically.

  The md5sums control file is generated automatically from the entire
  fileset, excluding any files under \"DEBIAN\".

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
   o chowns ROOT-OWNER [[str str]] "The list of root directories to file owner strings."]

  (when (and (seq chowns)
             (not= "root" (System/getProperty "user.name")))
    (util/warn "dpkg: %s\n%s\n%s\n"
               "You specified ownership changes to be made, but are not running as root."
               "It's very likely that this will NOT work."
               "Try running boot as root and setting BOOT_AS_ROOT=yes"))
  (when-not (and package version)
    (throw (Exception. "need package name and version to create deb package")))
  (let [tmp (core/tmp-dir!)
        chowns (lookup chowns)
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
          (copy! (core/tmp-file tmp-file) new-copy)))
      (change-ownership tmp chowns)
      (core/empty-dir! deb-tmp)
      (util/dosh "dpkg-deb" "--build" (.getAbsolutePath tmp) (.getAbsolutePath deb-file))
      (-> fileset
          (core/add-resource tmp)
          (core/add-resource deb-tmp)
          core/commit!))))
