# Contributing

This project is work of [many contributors](https://github.com/acm19/aws-request-signing-apache-interceptor/graphs/contributors).

You're encouraged to submit [pull requests](https://github.com/acm19/aws-request-signing-apache-interceptor/pulls), [propose features and discuss issues](https://github.com/acm19/aws-request-signing-apache-interceptor/issues).

In the examples below, substitute your Github username for `contributor` in URLs.

## Fork the Project

Fork the [project on Github](https://github.com/acm19/aws-request-signing-apache-interceptor) and check out your copy.

```
git clone https://github.com/contributor/aws-request-signing-apache-interceptor.git
cd aws-request-signing-apache-interceptor
git remote add upstream https://github.com/acm19/aws-request-signing-apache-interceptor.git
```

## Create a Topic Branch

Make sure your fork is up-to-date and create a topic branch for your feature or bug fix.

```
git checkout master
git pull upstream master
git checkout -b my-feature-branch
```

## Run Tests

Ensure that you can build the project and run tests.

```
mvn test
```

## Write Tests

Try to write a test that reproduces the problem you're trying to fix or describes a feature that you want to build. Add it to [src/test](src/test).

We definitely appreciate pull requests that highlight or reproduce a problem, even without a fix.

## Write Code

Implement your feature or bug fix.

Make sure that `mvn test` completes without errors.

## Integration Tests

To run integration tests against real infrastructure you can easily deploy a working cluster following [these instructions](infra/README.md).

## Write Documentation

Document any external behavior in the [README](README.md).

## Update Changelog

Add a line to [CHANGELOG](CHANGELOG.md) under *Next Release*.

Make it look like every other line, including your name and link to your Github account.

## Commit Changes

Make sure git knows your name and email address:

```
git config --global user.name "Your Name"
git config --global user.email "contributor@example.com"
```

Writing good commit logs is important. [A commit log should describe what changed and why](#commit-message-convention).

```
git add ...
git commit
```

## Commit Message Convention

Commit message should be [written as follows](https://github.com/torvalds/subsurface-for-dirk/blob/a48494d2fbed58c751e9b7e8fbff88582f9b2d02/README#L88):

> Header line: explain the commit in one line (use the imperative)
>
> Body of commit message is a few lines of text, explaining things in more detail, possibly giving some background about the issue being fixed, etc etc.
>
> The body of the commit message can be several paragraphs, and please do proper word-wrap and keep columns shorter than about 74 characters or so. That way "git log" will show things nicely even when it's indented.
>
> Make sure you explain your solution and why you're doing what you're doing, as opposed to describing what you're doing. Reviewers and your future self can read the patch, but might not understand why a particular solution was implemented.

If you are using `vim` to edit your commit messages the following configuration might be useful in your `~/.vimrc` to automatically add line breaks.
```
au FileType gitcommit set tw=72
```

> **_IMPORTANT:_** Add a reference to the PR at the end of the commit header surrounded by parenthesis, e.g. `(#32)`. You can use `git commit --amend` to edit your commit once you have the PR created.

> **_NOTE:_** A single commit is expected per push request to keep the GitLog clean and self-explanatory.

## Push

```
git push origin my-feature-branch
```

## Make a Pull Request

Go to https://github.com/contributor/aws-request-signing-apache-interceptor and select your feature branch.
Click the 'Pull Request' button and fill out the form. Pull requests are usually reviewed within a few days.

## Rebase

If you've been working on a change for a while, rebase with upstream/master.

```
git fetch upstream
git rebase upstream/master
git push origin my-feature-branch -f
```

## Update CHANGELOG Again

Update the [CHANGELOG](CHANGELOG.md) with the pull request number. Usually the message matches the [commit header](#commit-message-convention). A typical entry looks as follows.

```
* [#123](https://github.com/acm19/aws-request-signing-apache-interceptor/pull/123): Reticulate splines - [@contributor](https://github.com/contributor).
```

Amend your previous commit and force push the changes.

```
git commit --amend
git push origin my-feature-branch -f
```

## Check on Your Pull Request

Go back to your pull request after a few minutes and see whether it passed muster with CI. Everything should look green, otherwise fix issues and amend your commit as described above.

## Be Patient

It's likely that your change will not be merged and that the nitpicky maintainers will ask you to do more, or fix seemingly benign problems. Hang on there!

## Thank You

Please do know that we really appreciate and value your time and work. We love you, really.
