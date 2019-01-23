# Starting Cookie Syncs

This endpoint is used during cookie syncs. For technical details, see the
[Cookie Sync developer docs](../developers/cookie-syncs.md).

## POST /cookie_sync

### Sample Request
This returns a set of URLs to enable cookie syncs across bidders. (See Prebid.js documentation?) The request
must supply a JSON object to define the list of bidders that may need to be synced.

```
{
    "bidders": ["appnexus", "rubicon"],
    "gdpr": 1,
    "gdpr_consent": "BONV8oqONXwgmADACHENAO7pqzAAppY",
    "limit": 2
}
```

`bidders` is optional. If present, it limits the endpoint to return syncs for bidders defined in the list.

`gdpr` is optional. It should be 1 if GDPR is in effect, 0 if not, and omitted if the caller is unsure.

`gdpr_consent` is required if `gdpr` is `1`, and optional otherwise. If present, it should be an [unpadded base64-URL](https://tools.ietf.org/html/rfc4648#page-7) encoded [Vendor Consent String](https://github.com/InteractiveAdvertisingBureau/GDPR-Transparency-and-Consent-Framework/blob/master/Consent%20string%20and%20vendor%20list%20formats%20v1.1%20Final.md#vendor-consent-string-format-).

If `gdpr` is  omitted, callers are still encouraged to send `gdpr_consent` if they have it.
Depending on how the Prebid Server host company has configured their servers, they may or may not require it for cookie syncs.

`limit` is optional. If present and greater than zero, it will limit the number of syncs returned to `limit`, dropping some syncs to
get the count down to limit if more would otherwise have been returned. This is to facilitate clients not overloading a user with syncs
the first time they are encountered.

If the `bidders` field is an empty list, it will not supply any syncs. If the `bidders` field is omitted completely, it will attempt
to sync all bidders.

### Sample Response

This will return a JSON object that will allow the client to request cookie syncs with bidders that still need to be synced:

```
{
    "status": "ok",
    "bidder_status": [
        {
            "bidder": "appnexus",
            "usersync": {
                "url": "someurl.com",
                "type": "redirect",
                "supportCORS": false
            }
        }
    ]
}
```

### Special behavior for HOST-BIDDER

if `host-cookie` exists and `uids` cookie contains HOST-BIDDER uid with different value - the `host-cookie` value will be used.

So, available scenarios:
1. `host-cookie` specified, `uids.HOST-BIDDER` exists, `host-cookie` value **is equal** to  `uids.HOST-BIDDER`: no action (HAPPY PATH)
2. `host-cookie` specified, `uids.HOST-BIDDER` exists, `host-cookie` value **is NOT equal** to  `uids.HOST-BIDDER`: use `host-cookie` value for `uids.HOST-BIDDER`
3. `host-cookie` specified, no `uids.HOST-BIDDER`: use `host-cookie` value for `uids.HOST-BIDDER`
4. no `host-cookie`, `uids.HOST-BIDDER` exists: no action (continue use  `uids.HOST-BIDDER` existing value)
5. no `host-cookie`, no `uids.HOST-BIDDER`: no action

In both of cases 2 and 3 the `uids.HOST-BIDDER` is broken, but `host-cookie` is valid value. So, `uids.HOST-BIDDER` value should be updated.
In regular situation PBS just put pre-configured `usersync-url` in `/cookie_sync` response.
But for `HOST-BIDDER` it is not necessary to call `usersync-url`for obtaining new UID because we already have it in `host-cookie`.

So, all we need is to use direct `/setuid?bidder=%s&gdpr=%s&gdpr_consent=%s&uid=%s` url as `usersync-url` in `/cookie_sync` response to set http cookie to user.
