# Releasing

There are no hard rules about when to release `aws-request-signing-apache-interceptor`. Release bug fixes frequently, features not so frequently and breaking API changes rarely.

### Requirements

Apart from the obvious programs that you need to build and run the project locally: Git, Maven, Java, etc. You will need:
* [GitHub CLI](https://cli.github.com/).
* [GNU make](https://www.gnu.org/software/make/manual/make.html).
* [GNU sed](https://www.gnu.org/software/sed/).

### Release

Go the project root directory and run

```
make release
```

> **_NOTE:_** If you are not a maintainer, you might want to set the `REMOTE` env variable to your preferred remote name. The `create_pull_requests` target might fail, then you will need to create the PRs manually from the branches that were pushed.

### For Maintainers

Once a release is prepared 2 pull requests will be generated. They have to be merged in order. First the one for the release and then the one for the next development version (it should be a draft PR). The automation will take care of the rest.

> **_NOTE:_** Please review the changes in case there are errors if the PR were modified after the automation ran.

#### Versioning

This project follows [Semantic Versioning 2.0.0 (semver)](https://semver.org/spec/v2.0.0.html).

> **_NOTE:_** It's responsibility of the committer to update the version number within the same pull request where the change was introduced when the changes affect `minor` or `major` as follows:

Given a version number MAJOR.MINOR.PATCH, increment the:

* `MAJOR` version when you make incompatible API changes,
* `MINOR` version when you add functionality in a backwards compatible manner, and
* `PATCH` version when you make backwards compatible bug fixes.

