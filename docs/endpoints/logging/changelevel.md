# Change logging level endpoint

This endpoint has a path `/logging/changelevel` by default (can be configured).

This endpoint allows changing `org.prebid.server` logger level temporarily, mainly for troubleshooting production issues.

### Query Params
- `level` - desired logging level to set; must be one of `error`, `warn`, `info`, `debug`
- `duration` - for how long to change level before it gets reset to original; there is an upper threshold for this 
value set in [configuration](../../config-app.md)
