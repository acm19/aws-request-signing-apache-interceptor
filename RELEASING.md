# Releasing

There are no hard rules about when to release `aws-request-signing-apache-interceptor`. Release bug fixes frequently, features not so frequently and breaking API changes rarely.

### For Maintainers

To release manually run `Prepare Release` in the `master` branch. Make sure no commits are pushed to `master` during the time this job is running to avoid race conditions.

#### Versioning

This project follows [Semantic Versioning 2.0.0 (semver)](https://semver.org/spec/v2.0.0.html).

> **_NOTE:_** It's responsibility of the committer to update the version number within the same pull request where the change was introduced when the changes affect `minor` or `major` as follows:

Given a version number `MAJOR.MINOR.PATCH`, increment the:

* `MAJOR` version when you make incompatible API changes,
* `MINOR` version when you add functionality in a backwards compatible manner, and
* `PATCH` version when you make backwards compatible bug fixes.

