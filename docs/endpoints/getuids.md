# Getting User Syncs

This endpoint is used by bidders to obtain user IDs with Prebid Server.
The response body is empty for this request.
If a user opted out then response cookie will contain empty UIDs with active `optOut` flag.

## Sample request

`GET http://prebid.site.com/getuids`

This will response like:
```json
{"tempUIDs":{},"bday":"2018-02-22T11:39:26.517000000Z"}
```
