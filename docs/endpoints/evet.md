# Event Endpoint

Allows Web browsers and mobile applications to notify about different ad events (win, view etc).

## `GET /event` 

This endpoint is used to notify about event and do request for tracking pixel if needed.

### Query Params

- `type`: Type of the event. Allowed values: win, view. Required parameter.
- `bidid`: Bid ID, expected to be unique at least within single bidder. Required parameter.
- `bidder`: Bidder code. Required parameter.
- `format`: Format of the tracking pixel image. Allowed values: png, jpg. Not required parameter.

If “format” query parameter is present in the request URL, response body will contain a blank tracking pixel and “Content-Type” header will specify corresponding image format (“image/png” or “image/jpeg”).

### Sample request

`GET http://prebid.site.com/event?type=win&bidid=12345&bidder=rubicon&format=jpg`