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

> **_NOTE:_** If you are not a maintainer, you might want to set the `REMOTE` env variable to your preferred remote name. The `create_pull_requests` target might fail and you will need to create the PRs manually from the branches that were pushed.

### For Maintainers

Once a release is prepared 2 Pull Request will be generated. They have to be merged in order. First the one for the release and then the one for the next development version (it should be a draft PR). The automation will take care of the rest.

> **_NOTE:_** Please review the changes in case there are errors if the PR were modified after the automation ran.
