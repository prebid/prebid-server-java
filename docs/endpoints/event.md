# Event Endpoint

Allows Web browsers and mobile applications to notify about different ad events (win, imp etc).

## `GET /event` 

This endpoint is used to notify about event and do request for tracking pixel if needed.

### Query Params

- `t`: Type of the event. Allowed values: `win` or `imp`. Required parameter.
- `a`: Account ID. Required parameter.
- `b`: Bid ID, expected to be unique at least within single bidder. Required parameter.
- `bidder`: Bidder code. Required parameter.
- `f`: Format of the PBS response. Allowed values:
  - `b`: blank, just return HTTP 200 with an empty body
  - `i`: image, return HTTP 200 with a blank PNG body
- `ts`: auction timestamp
- `x` : Disables or enables analytics. Allowed values: `1` to enable analytics or `0` to disable. `1` is default.
### Sample request

`GET http://prebid.site.com/event?type=win&bidid=12345&a=1111&bidder=rubicon&f=b`
