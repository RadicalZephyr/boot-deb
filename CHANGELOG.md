# Change Log
All notable changes to this project will be documented in this
file. This change log follows the conventions of
[keepachangelog.com](http://keepachangelog.com/).

## [Unreleased]
[Unreleased]: https://github.com/radicalzephyr/boot-dpkg/compare/0.3.0...HEAD

### Fixed

- Format the contents of the `:depends` key correctly in `control` file

## [0.3.0] - 2018-10-13
### Added

- Generate conffiles listing automatically from all included files
  under `/etc`
- New --conf-files option for specifying additional paths

### Fixed

- When the `dpkg` task is not passed required options it now actually fails

### Changed

- Package creation is now done "by hand" instead of shelling out to
  `dpkg-deb`
- Change format of chowns argument from <PATH>-<OWNER> to
  <PATH>:<OWNER> and data coercion from a vector of pairs to a map

[0.3.0]: https://github.com/radicalzephyr/boot-dpkg/compare/0..0...0.3.0

## [0.2.0] - 2018-10-11
### Fixed

- Files under `DEBIAN/` are no longer erroneously included in the
  md5sums listing

[0.2.0]: https://github.com/radicalzephyr/boot-dpkg/compare/0.1.0...0.2.0

## [0.1.0] - 2018-04-16
### Added

- Add `dpkg` task

[0.1.0]: https://github.com/radicalzephyr/boot-dpkg/compare/3d0c43f...0.1.0
