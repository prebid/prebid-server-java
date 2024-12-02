### 🔧 Type of changes
- [ ] new bid adapter
- [ ] bid adapter update
- [ ] new feature
- [ ] new analytics adapter
- [ ] new module
- [ ] module update
- [ ] bugfix
- [ ] documentation
- [ ] configuration
- [ ] dependency update
- [ ] tech debt (test coverage, refactorings, etc.)

### ✨ What's the context?
What's the context for the changes?

### 🧠 Rationale behind the change
Why did you choose to make these changes? Were there any trade-offs you had to consider?

### 🔎 New Bid Adapter Checklist
- [ ] verify email contact works
- [ ] NO fully dynamic hostnames
- [ ] geographic host parameters are NOT required
- [ ] direct use of HTTP is prohibited - *implement an existing Bidder interface that will do all the job*
- [ ] if the ORTB is just forwarded to the endpoint, use the generic adapter - *define the new adapter as the alias of the generic adapter*
- [ ] cover an adapter configuration with an integration test

### 🧪 Test plan
How do you know the changes are safe to ship to production?

### 🏎 Quality check
- [ ] Are your changes following [our code style guidelines](https://github.com/prebid/prebid-server-java/blob/master/docs/developers/code-style.md)?
- [ ] Are there any breaking changes in your code?
- [ ] Does your test coverage exceed 90%?
- [ ] Are there any erroneous console logs, debuggers or leftover code in your changes?
