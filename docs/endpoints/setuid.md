# Saving User Syncs

This endpoint is used by bidders to sync user IDs with Prebid Server.
If a user runs an [auction](./openrtb2/auction.md) _without_ specifying `request.user.buyeruid`,
then Prebid Server will set it to the uid saved here before forwarding the request to the Bidder.

## `GET /setuid`

This endpoint can be used to save UserIDs for a Bidder. These UIDs will be saved in a Cookie,
so they will not translate across Prebid Server instances hosted on different domains.

Saved IDs will be recognized for 7 days before being considered "stale" and being re-synced.

### Query Params

- `bidder`: The FamilyName of the bidder which is being synced.
- `uid`: The User's ID in the given domain.
- `gdpr`: This should be `1` if GDPR is in effect, `0` if not, and undefined if the caller isn't sure.
- `gdpr_consent`: This is required if `gdpr` is one, and optional (but encouraged) otherwise. If present, it should be an [unpadded base64-URL](https://tools.ietf.org/html/rfc4648#page-7) encoded [Vendor Consent String](https://github.com/InteractiveAdvertisingBureau/GDPR-Transparency-and-Consent-Framework/blob/master/Consent%20string%20and%20vendor%20list%20formats%20v1.1%20Final.md#vendor-consent-string-format-).

If the `gdpr` and `gdpr_consent` params are included, this endpoint will _not_ write a cookie unless
the Vendor ID set by the Prebid Server host company has permission to save cookies for that user.

If in doubt, contact the company hosting Prebid Server and ask if they're GDPR-ready.

### Sample request

`GET http://prebid.site.com/setuid?bidder=adnxs&uid=12345&gdpr=1&gdpr_consent=BONciguONcjGKADACHENAOLS1rAHDAFAAEAASABQAMwAeACEAFw`
