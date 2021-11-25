# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## Unreleased

### Changed
* Check existing header and only update if violations exist

## 0.7.0 - 2021-11-24

### Added
* Support files without extension like Dockerfile, Jenkinsfile or Vagrantfile
* Option to ignore the next line

### Changed
* Allow whitespaces in empty line

### Fixed
* Ignore shebang in files with block comment
