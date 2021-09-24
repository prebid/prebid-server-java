# Tracelog Endpoint

This endpoint is available on admin port called `/pbs-admin/tracelog`.

## POST `/pbs-admin/tracelog`
 
 Allows to configure logging level for specific account, line item and bidder code during some defined time period.

### Query parameters:

This endpoint supports the following query parameters:

 1. `account` - specified an account for which logging level should be changed. (Not required, no default value) 
 2. `lineItemId` - specified a lineItemId for which logging level should be changed. (Not required, no default value) 
 3. `bidderCode`- specified a bidderCode for which logging level should be changed. (Not required, no default value) 
 4. `level` - specified a log level to which logs should be updated. Allowed values are `info`, `warn`, `trace`,
 `error`, `fatal`, `debug`. Default value if not defined is `error`. (Not required) 
 5. `duration` - time in seconds during which changes will be applied. (Required).

At least one of `account`, `lineItemId` or `bidderCode` should be specified. If more than one specified,
logic conjuction (and operation) is applied to parameters.

### Request samples

`GET http://prebid.site.com/pbs-admin/tracelog?account=1234&duration=100` - updates logging level to `error` level for account 1234
for 100 seconds.

`GET http://prebid.site.com/pbs-admin/tracelog?account=1234&bidder=rubicon&lineItemId=lineItemId1&level=debug&duration=100` - updates
logging level to warn, for account = 1234 and bidder = rubicon and lineItemId = lineItemId1 for 100 seconds.