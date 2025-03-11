# Porting Guide

## Overview

First, thank you for taking on the migration of an adapter from Go to Java. But really the best way to think of it is not as straight port. Instead, we recommend treat this task as a re-implementation. It will take a few adapters before you fully get the hang of it, and that's okay—everyone goes through a learning curve.

Keep in mind that the PBS-Go team is more lenient about what they allow in adapters compared to the PBS-Java team.

## Pull Request Requirements

We would appreciate it if your porting PR title follows these patterns:

- `Port <adapter_name>: New Adapter` – For porting a completely new adapter to the project (e.g., `Port Kobler: New Adapter`).
- `Port <adapter_name>: <update_description>` – For porting a specific update to an existing adapter (e.g., `Port OpenX: Native Support`).
- `Port <alias_name>: New alias for <adapter_name>` – For porting an alias of an existing adapter to the project (e.g., `Port Artechnology: New alias of StartHub`).

Additionally, we kindly ask that you:

- Link any existing GitHub issues that your PR resolves. This ensures the issue will be automatically closed when your PR is merged.
- Add the label `do not port` to your PR.

## Porting Requirements

1. **Feature Parity**: A Java adapter should have the same functionality as the Go adapter.
2. **Java Adapter Code Should:**
    - Follow the code style of the PBS-Java repository (see [the code style page](code-style.md)).
    - Be well-written Java code: clear, readable, optimized, and following best practices.
    - Maintain a structure similar to existing adapters (see below).
3. **The adapter should be covered with tests:**
    - Unit tests for implementation details.
    - A simple integration test to ensure the adapter is reachable, can send requests to the bidder, and that its configuration works.

### What does "having a similar structure to existing adapters" mean?

The PBS-Java codebase has evolved over time. While existing adapters may not be perfect and could contain legacy issues (e.g., using outdated Java syntax), they still serve as a valuable reference for learning, inspiration, and even reuse.

Each adapter is unique, but most share common patterns. For example, nearly every adapter includes:

1. **A `makeHttpRequests(...)` method**
    - Iterates over the `imps` in the bid request:
        - Parses `imp[].ext.prebid.bidder` (i.e., bidder static parameters).
        - Modifies the `imp`.
        - Collects errors encountered during `imp` processing.
    - Prepares outgoing request(s):
        - Constructs headers.
        - Builds the request URL.
        - Modifies the incoming bid request based on the updated `imps`.

2. **A `makeBids(...)` method**
    - Parses the `BidResponse`.
    - Iterates over `seatBids` and `bids`.
    - Creates a list of `BidderBid` objects.

### Ensuring Structural Consistency

To maintain consistency across adapters:
- Fit the Go adapter functionality into the Java adapter structure.
- Use the same or similar method and variable names where applicable.
- Reuse existing solutions for common functionality (e.g., use `BidderUtil`, `HttpUtil` classes).
- Ensure unit tests follow a similar structure, with comparable test cases and code patterns.

## Specific Rules and Tips for Porting

1. Begin by determining how the Go adapter's functionality fits into the Java adapter structure.
2. Go adapters deserialize JSON objects in-place, while Java adapters work with pre-deserialized objects. As a result, many errors thrown in the Go version do not apply in Java.
3. **No hardcoded URLs.** If an adapter has a "test URL," it must be defined in the YAML file. See `org.prebid.server.spring.config.bidder.NextMillenniumConfiguration.NextMillenniumConfigurationProperties` for an example of how to handle special YAML entries.
4. The structure of Go and Java bidder configuration files differs—do not copy and paste directly. Pay attention to details such as macros in the endpoint and redirect/iframe URLs.
5. **Prohibited in bidder adapters:**
    - Blocking code.
    - Fully dynamic hostnames in URLs.
    - Non-thread-safe code (bidder adapters should not store state internally).
6. If an adapter has no special logic, consider using an alias to `Generic` instead. In this case, there will still need to be an integration test for this bidder. e.g. src/test/java/org/prebid/server/it/BidderNameTest.java
