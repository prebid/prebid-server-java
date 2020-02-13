# Admin Endpoint

Unavailable if `logger level modifier` is disabled (`logger-level-modifier.enabled` config property)

This `/pbs-admin/admin` endpoint can be configured:
 - `admin-endpoints.logger-level-modifier.on-application-port` - when equals to `false` endpoint will be bound to `admin.port`.
 - `admin-endpoints.logger-level-modifier.protected` - when equals to `true` endpoint will be protected by basic authentication configured in `admin-endpoints.credentials` 

This endpoint will set the desirable logging level and number of logs for `400` responses.

### Query Params

- `logging` - Desirable logging level: `info`, `warn`, `trace`, `error`, `fatal`, `debug`.
- `records` - numbers of logs with changed logging level. (0 < n < 100_000)
