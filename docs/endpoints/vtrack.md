# Event Endpoint

This endpoint is used to cache puts in request.

## `POST /vtrack` 

Inserts `bidId` and `accountId` into json parameter `value`, depending on the `modifyingVastXmlAllowed` bidder flag.
POST the modified JSON to Prebid Cache, and forward the results to the client.

Response body example:

```json
{"responses":[{"uuid":"94531ab8-c662-4fc7-904e-6b5d3be43b1a"}]}
```

### Query Params

- `a`: Account id. Required parameter if there are any bidders with `modifyingVastXmlAllowed` equals true.

### Sample request

`POST https://prebid-server.rubiconproject.com/vtrack?a=ACCOUNT`

```json
{"puts":[{
    "bidid": "BIDID",
    "bidder": "BIDDER",
    "type":"xml",
    "value":"<VAST…/VAST>",
    "ttlseconds":3600
}]}
```
