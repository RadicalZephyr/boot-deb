# Change Log
All notable changes to this project will be documented in this
file. This change log follows the conventions of
[keepachangelog.com](http://keepachangelog.com/).

## [Unreleased]

[Unreleased]: https://github.com/radicalzephyr/boot-dpkg/compare/0.2.0...HEAD
### Added

- Generate conffiles listing automatically from all included files
  under `/etc`
- New --conf-files option for specifying additional paths

### Fixed

- When the `dpkg` task is not passed required options it now actually fails

## [0.2.0] - 2018-10-11
### Fixed

- Files under `DEBIAN/` are no longer erroneously included in the
  md5sums listing

[0.1.0]: https://github.com/radicalzephyr/boot-dpkg/compare/0.1.0...0.2.0

## [0.1.0] - 2018-04-16
### Added

- Add `dpkg` task

[0.1.0]: https://github.com/radicalzephyr/boot-dpkg/compare/3d0c43f...0.1.0
