# Admin Endpoint

This `/admin` endpoint is bound to `admin.port`.

Unavailable if `logger level modifier` is disabled (`logger-level-modifier.enabled` config property)

This endpoint will set the desirable logging level and number of logs for `400` responses.

### Query Params

- `logging`: Desirable logging level: `info`, `warn`, `trace`, `error`, `fatal`, `debug`.
- `records`: numbers of logs with changed logging level. (0 < n < 100_000)
