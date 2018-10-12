# boot-dpkg

[](dependency)
```clojure
[radicalzephyr/boot-dpkg "0.2.0"] ;; latest release
```
[](/dependency)

A Boot task for generating debian binary packages.

- Provides a `dpkg` task

## Usage

Set up a task pipeline to create the files you wish to include in the
debian package contents. You probably also want to aggressively filter
out any files in the fileset that should _not_ be included in the
package contents.

Then, configure the `dpkg` task with the details of the debian package
control file:

``` clojure
(task-options!
  dpkg {:package "package-name
        :version "0.1.0-1"
        :section "admin"
        :architecture "all"
        :maintainer "Geoff Shannon"
        :description "My Super awesome package!"})
```

### Control Files

The `control` file is generated automatically by this task based on
the options given, and the `md5sums` file is created automatically
with the hashes for every file in the fileset. If you wish to include
other control files like maintainer scripts, simply create a `DEBIAN`
folder somewhere on your `:resource-paths` and name the files
accordingly.

``` clojure
(set-env!
 :resource-paths  #{"resources"})
```

```
resources/DEBIAN/
├── postinst
├── postrm
├── preinst
└── prerm
```

## License

Copyright © 2018 Geoff Shannon

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
