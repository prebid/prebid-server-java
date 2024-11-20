# Code Reviews

## Standards
Anyone is free to review and comment on any [open pull requests](https://github.com/prebid/prebid-server-java/pulls).

1. PRs that touch only adapters and modules can be approved by one reviewer before merge.
2. PRs that touch PBS-core must be reviewed and approved by at least two 'core' reviewers before merge.

## Process

New pull requests should be [assigned](https://help.github.com/articles/assigning-issues-and-pull-requests-to-other-github-users/) 
to a reviewer for review within 5 business days of being opened.
That person should either approve the changes or request changes within 5 business days of being assigned.
If they're too busy, they should assign it to someone else who can review it within that timeframe.

After the review, if the PR touches PBS-core, it must be assigned to a second reviewer.

## Priorities

Code reviews should focus on things which cannot be validated by machines.

Some examples include:

- Can we improve the user's experience in any way?
- Have the relevant [docs]() been added or updated? If not, add the `needs docs` label.
- Do you believe that the code works by looking at the unit tests? If not, suggest more tests until you do!
- Is the motivation behind these changes clear? If not, there must be [an issue](https://github.com/prebid/prebid-server-java/issues) 
explaining it. Are there better ways to achieve those goals?
- Does the code use any global, mutable state? [Inject dependencies](https://en.wikipedia.org/wiki/Dependency_injection) instead!
- Can the code be organized into smaller, more modular pieces?
- Is there dead code which can be deleted? Or TODO comments which should be resolved?
- Look for code used by other adapters. Encourage adapter submitter to utilize common code.
- Specific bid adapter rules:
    - Host endpoints cannot be fully dynamic. i.e. they can utilize "https://REGION.example.com", but not "https://HOST".
    - They cannot _require_ a "region" parameter. Region may be an optional parameter, but must have a default.
