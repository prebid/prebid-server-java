# `GET /status`

This endpoint returns a 2xx response and a list various of health check results. 

Response body example:

```json
{
"application": {
  "status": "ready"
},
"database": {
  "status": "UP",
  "last_updated": "2019-04-17T13:20:45.236Z"
}
}
```

## Health Checkers
### Application Checker

Checks the application status from [config](../config.md) property `status-response`. 
This is the key property that enables other Health Checkers and indicated that Prebid Server is ready to serve the requests.

For example, in `application.yaml`:

```yaml
status-response: "ok"
```
In case `status-response` property is empty or missing, the endpoint will return HTTP Response 204 'No Content' with an empty body.

### Database Checker

Periodically checks the database status.
Can be enabled or disabled by `health-check.database.enabled` property.
Checks interval can be specified at `health-check.database.refresh-period-ms` property.

Config example:

```yaml
health-check:
  database:
    enabled: true,
    refresh-period-ms: 60000
```
