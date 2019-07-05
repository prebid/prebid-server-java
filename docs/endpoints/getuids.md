# Getting User Syncs

This endpoint is used by bidders to obtain user IDs with Prebid Server.

## Sample request

`GET http://prebid.site.com/getuids`

This will response like:
```json
{"buyeruids":{"adnxs":"appnexus-uid","rubicon":"rubicon-uid"}}