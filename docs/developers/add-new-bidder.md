# Adding a New Bidder

This document describes how to add a new Bidder to Prebid Server.

## Choose a Bidder Name

This name must be unique. Existing BidderNames can be found [here](../../src/main/java/org/prebid/server/bidder).

Throughout the rest of this document, substitute `{bidder}` with the name you've chosen.

## Define your Bidder Params

Bidders may define their own APIs for Publishers pass custom values. It is _strongly encouraged_ that these not
duplicate values already present in the [OpenRTB 2.5 spec](https://www.iab.com/wp-content/uploads/2016/03/OpenRTB-API-Specification-Version-2-5-FINAL.pdf).

Publishers will send values for these parameters in `request.imp[i].ext.{bidder}` of
[the Auction endpoint](../endpoints/openrtb2/auction.md). Prebid Server will preprocess these so that
your bidder will access them at `request.imp[i].ext.bidder`--regardless of what your `{bidder}` name is.

## Implement your Bidder

Bidder implementations are scattered throughout several files:

- `static/bidder-params/{bidder}.json`: A [draft-4 json-schema](https://spacetelescope.github.io/understanding-json-schema/) which [validates your Bidder's params](https://www.jsonschemavalidator.net/).
- `src/main/java/org/prebid/server/bidder{bidder}/{bidder}.java`: contains an implementation of [the Bidder interface](../../src/main/java/org/prebid/server/bidder/Bidder.java).
- `src/main/java/org/prebid/server/model/openrtb/ext/{bidder}`: contract classes for your Bidder's params.
- `src/main/java/org/prebid/server/spring/config/BidderConfiguration.java`: contains Bidder integration.
- `src/test/java/org/prebid/server/bidder/{bidder}Test.java`: unit tests for your Bidder implementation.

Bidder implementations may assume that any params have already been validated against the defined json-schema.

## Test your Bidder

### Automated Tests

Assume common rules to write unit tests from [here](unit-tests.md).

Bidder tests live in two files:

- `src/test/java/org/prebid/server/bidder/{bidder}Test.java`: contains unit tests for your Bidder implementation.

Commonly you should write tests for covering:
- creation of your Bidder implementation.
- correct Bidder's params filling.
- JSON parser errors handling.
- specific cases for composing requests to exchange.
- specific cases for processing responses from exchange.

Do not forget to add your Bidder to `org.prebid.server.ApplicationTest.openrtb2AuctionShouldRespondWithBidsFromDifferentExchanges`.

We expect to see at least 90% code coverage on each Bidder.

### Manual Tests

[Configure](../config.md), [build](../build.md) and [start](../run.md) your server.

Then `POST` an OpenRTB Request to `http://localhost:8000/openrtb2/auction`.

If at least one `request.imp[i].ext.{bidder}` is defined in your Request, then your bidder should be called.

To test user syncs, [save a UID](../endpoints/setuid.md) using the FamilyName of your Bidder.
The next time you use `/openrtb2/auction`, the OpenRTB request sent to your Bidder should have
`BidRequest.User.BuyerUID` with the value you saved.

## Contribute

Finally, [Contribute](../contributing.md) your Bidder to the project.
