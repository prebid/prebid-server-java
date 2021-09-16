# Line Item status

This endpoint is available on admin port called `/pbs-admin/lineitem-status`.

Giving read-only access to defined in parameters line item. Contains information about active delivery schedule,
ready at timestamp, spent tokens number and pacing frequency in milliseconds.

## `GET /pbs-admin/lineitem-status?id=<lineItemId>`

### Query parameters:

This endpoint supports the following query parameters:

`id` - line item id indicate a the line item about which information is needed.

### Sample request

`GET http://prebid.site.com/pbs-admin/lineitem-status?id=lineItemId1`