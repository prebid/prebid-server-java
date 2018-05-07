## `GET /status`

This endpoint will return a 2xx response whenever Prebid Server is ready to serve requests.
Its exact response can be [configured](../config.md) with the `status-response`
config option. For example, in `application.yaml`:

```yaml
status-response: "ok"
```