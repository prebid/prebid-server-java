# Cookie Sync Technical Details

This document describes the mechanics of a Prebid Server cookie sync.

## Motivation

Many Bidders track users through Cookies. Since Bidders will generally serve ads from a different domain
than where Prebid Server is hosted, the values of those cookies must be consolidated under the Prebid Server domain so
that they can be sent to each demand source in [/openrtb2/auction](../endpoints/openrtb2/auction.md) calls.

## How to do it?

Prebid.js starts the process by calling Prebid Server on the [`/cookie_sync`](../endpoints/cookieSync.md) endpoint, POSTing which bidders are involved S2S header bidding on the page. For each bidder, Prebid Server will build up a response by checking the `uids` cookie. If the bidder already has an entry in the `uids` cookie, the `response.bidder_status` is set to `ok`,  otherwise
call `GET element.usersync.url` and add it to the response. The usersync.url endpoint is set up by each bidder and needs to respond with a redirect to `/setuid` which will complete the cookie sync.

## Mechanics

Bidders who support cookie syncs must implement an endpoint under their domain which accepts
an encoded URI for redirects. The bid adapter sets

For example, the Rubicon Project usersync.url is set as:
> https://pixel.rubiconproject.com/exchange/sync.php?p=PBS_CODE&gdpr={{gdpr}}&gdpr_consent={{gdpr_consent}}

Note that the {{gdpr}} macros here will be resolved by Prebid Server to values supplied in the /cookie_sync POST. Rubicon Project's endpoint looks at the PBS_CODE provided (provided by Rubicon to each Prebid Server host company) and redirects to the appropriate /setuid URL.

Another example:

> some-bidder-domain.com/usersync-url?redirectUri=www.prebid-domain.com%2Fsetuid%3Fbidder%3Dsomebidder%26uid%3D%24UID%26gdpr

In this example, the endpoint would URL-decode the `redirectUri` param to get `www.prebid-domain.com/setuid?bidder=somebidder&uid=$UID`.
It would then replace the `$UID` macro with the user's ID from their cookie. Supposing this user's ID was "132",
it would then return a redirect to `www.prebid-domain.com/setuid?bidder=somebidder&uid=132`.

Prebid Server would then save this ID mapping of `somebidder: 132` under the cookie at `prebid-domain.com`.

Now when the client then calls `www.prebid-domain.com/openrtb2/auction`, the ID for `somebidder` will be available in the Cookie.
Prebid Server will then stick this into `request.user.buyeruid` in the OpenRTB request it sends to `somebidder`'s Bidder.
