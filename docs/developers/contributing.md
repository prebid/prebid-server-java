# Contributing

## Create an issue

[Create an issue](https://github.com/prebid/prebid-server-java/issues/new) describing the motivation for your changes.
Are you fixing a bug? Improving documentation? Optimizing some slow code?

Pull Requests without associated Issues may still be accepted, if the motivation is obvious.
However, this will help speed up code review if there's any uncertainty.

## Change the code

[Create a fork](https://help.github.com/articles/working-with-forks/) and make your code changes.

## Add tests

All pull requests must have 90% coverage in the changed code. Check the code coverage with your IDE or external tools.

Bugfixes should include a regression test which prevents that bug from being re-introduced in the future.

## Update Documentation

Documentation for the project is stored in the [docs]() directory. If your feature requires docs updates,
those updates must be submitted in the same Pull Request as the code changes.

- [docs/endpoints](endpoints) describes the Prebid Server API. For example, the endpoint `host:port/openrtb2/auction` is described by [docs/endpoints/openrtb2/auction.md](endpoints/openrtb2/auction.md)
- [docs/developers]() contains docs intended for Developers. These assume that the reader is technical, and describe the mechanics of features.

## Open a Pull Request

When you're ready, [submit a Pull Request](https://help.github.com/articles/creating-a-pull-request/)
against the `master` branch of [our GitHub repository](https://github.com/prebid/prebid-server-java/compare).

If the tests pass locally, but fail on your PR, [update your fork](https://help.github.com/articles/syncing-a-fork/) with the latest code from `master`.
