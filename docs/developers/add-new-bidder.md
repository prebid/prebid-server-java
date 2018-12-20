# Adding a New Bidder

This document describes how to add a new Bidder to Prebid Server. Bidders are responsible for reaching out to your Server to fetch Bids.

**NOTE**: To make everyone's lives easier, Bidders are expected to make Net bids (e.g. "If this ad wins, what will the publisher make?), not Gross ones.
Publishers can correct for Gross bids anyway by setting [Bid Adjustments](../endpoints/openrtb2/auction.md#bid-adjustments) to account for fees.

## Choose a Bidder Name

This name must be unique. Existing BidderNames can be found [here](../../src/main/java/org/prebid/server/bidder).

Throughout the rest of this document, substitute `{bidder}` with the name you've chosen.

## Define your Bidder Params

Bidders may define their own APIs for Publishers pass custom values. It is _strongly encouraged_ that these not
duplicate values already present in the [OpenRTB 2.5 spec](https://www.iab.com/wp-content/uploads/2016/03/OpenRTB-API-Specification-Version-2-5-FINAL.pdf).

Publishers will send values for these parameters in `request.imp[i].ext.{bidder}` of
[the Auction endpoint](../endpoints/openrtb2/auction.md). Prebid Server will preprocess these so that
your bidder will access them at `request.imp[i].ext.bidder`--regardless of what your `{bidder}` name is.

## Configuration

Add default configuration properties for your Bidder to `src/main/resources/bidder-config/{bidder}.yaml` file.
For more information about application configuration see [here](../config.md)

## Implementation

Bidder implementations are scattered throughout several files:
- `src/main/java/org/prebid/server/bidder/{bidder}/{bidder}MetaInfo.java`: contains metadata (e.g. contact email, platform & media type support) about the Bidder.
- `src/main/java/org/prebid/server/bidder/{bidder}/{bidder}Bidder.java`: contains an implementation of [the Bidder interface](../../src/main/java/org/prebid/server/bidder/Bidder.java).
- `src/main/java/org/prebid/server/bidder/{bidder}/{bidder}Adapter.java`: contains an implementation of [the Adapter interface](../../src/main/java/org/prebid/server/bidder/Adapter.java).
- `src/main/java/org/prebid/server/bidder/{bidder}/{bidder}Usersyncer.java`: contains an implementation of [the Usersyncer interface](../../src/main/java/org/prebid/server/bidder/Usersyncer.java).
- `src/main/java/org/prebid/server/proto/openrtb/ext/{bidder}`: contract classes for your Bidder's params.
- `src/main/resources/static/bidder-params/{bidder}.json`: A [draft-4 json-schema](https://spacetelescope.github.io/understanding-json-schema/) which [validates your Bidder's params](https://www.jsonschemavalidator.net/).

Bidder implementations may assume that any params have already been validated against the defined json-schema.

### Generic OpenRTB Bidder

There's an option to implement a bidder by using a pre-existing template.
OpenrtbBidder(../../src/main/java/org/prebid/server/bidder/OpenrtbBidder.java) is an abstract class that
implements Bidder<BidRequest> interface and provides a default implementation of its methods.

This class provides a fixed algorithm with number of certain access points(so called hook-methods) that
could be overridden to change the defaults to implement bidder-specific transformations.
You can check what "hooks" are available and their description at the OpenrtbBidder class.

NOTE: this model is not universal "all-in-one" solution as it encapsulates only the simple bidders' behaviour
in order to ease the creation of lightweight bidders and get rid of boilerplate code.
Bidders with a complicated request transformation logic would have to implement a Bidder interface and
define their structure from scratch.

See "Can our Bidder use OpenrtbBidder model?" for list of requirements.

#### What OpenRTB Bidder implements?

Constructing outgoing http request/s from incoming bid request:

1. Validate incoming bid request, request impressions and impression extensions;
2. Apply necessary changes or transformations to request and its impressions;
3. Encode and return the modified outgoing request/s.

Bidder response processing:

1. Extract bids from response;
2. Set each bid type and currency;

#### Can our Bidder use OpenrtbBidder model?

If your bidder is the one that:

1. Send out a single request i.e. ones that just modify an incoming request and pass it on("one-to-one") OR
send out one request per incoming request impression. Other "one-to-many" scenarios are nto supported;
2. Have a constant endpoint url (no additional or optional path parameters or request parameters);
3. Require a basic set of headers, which is "Content-type" : "application/json;charset=utf-8" and "Accept" : "application/json"
4. Apply static changes to the outgoing request, e.g., setting a specific constant value or removing a certain request field;
5. Modify impression or request values in a way that could be expressed by transformation mapping;
6. Returns all bids present in Bid Response;
7. Bid type and currency could by derived from bid itself and its corresponding impression;

## Integration

After implementation you should integrate the Bidder with file:
- `src/main/java/org/prebid/server/spring/config/bidder/{bidder}Configuration.java`

It should be public class with Spring `@Configuration` annotation so that framework could pick it up.

This file consists of three main parts:
- the constant `BIDDER_NAME` with the name of your Bidder.
- injected configuration properties (like `endpoint`, `usersyncUrl`, etc) needed for the Bidder's implementation.
- declaration of `BidderDeps` bean combining _bidder name_, _Usersyncer_, _Adapter_ and _BidderRequester_ in one place as a single point-of-truth for using it in application.

Also, you can add `@ConditionalOnProperty` annotation on configuration if bidder has no default properties.
See `src/main/java/org/prebid/server/spring/config/bidder/FacebookConfiguration.java` as an example.

## Testing

### Automated Tests

Assume common rules to write unit tests from [here](unit-tests.md).

Bidder tests live in the next files:
- `src/test/java/org/prebid/server/bidder/{bidder}/{bidder}BidderTest.java`: unit tests for your Bidder implementation.
- `src/test/java/org/prebid/server/bidder/{bidder}/{bidder}AdapterTest.java`: unit tests for your Adapter implementation.
- `src/test/java/org/prebid/server/bidder/{bidder}/{bidder}UsersyncerTest.java`: unit tests for your Usersyncer implementation.

Commonly you should write tests for covering:
- creation of your Adapter/Bidder/Usersyncer implementations.
- correct Bidder's params filling.
- JSON parser errors handling.
- specific cases for composing requests to exchange.
- specific cases for processing responses from exchange.

Do not forget to add your Bidder to `ApplicationTest.java` tests.

We expect to see at least 90% code coverage on each bidder.

### Manual Tests

[Configure](../config.md), [build](../build.md) and [start](../run.md) your server.

Then `POST` an OpenRTB Request to `http://localhost:8000/openrtb2/auction`.

If at least one `request.imp[i].ext.{bidder}` is defined in your Request, then your bidder should be called.

To test user syncs, [save a UID](../endpoints/setuid.md) using the FamilyName of your Bidder.
The next time you use `/openrtb2/auction`, the OpenRTB request sent to your Bidder should have
`BidRequest.User.BuyerUID` with the value you saved.

## Contribute

Finally, [Contribute](../contributing.md) your Bidder to the project.

## Server requirements
 
**Note**: In order to be part of the auction, all bids must include:
 
- An ID
- An ImpID which matches one of the `Imp[i].ID`s from the incoming `BidRequest`
- A positive `Bid.Price`
- A `Bid.CrID` which uniquely identifies the Creative in the bid.

Bids which don't satisfy these standards will be filtered out before Prebid Server responds.
