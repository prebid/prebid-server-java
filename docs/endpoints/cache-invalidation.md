# Cache Invalidation

Unavailable if in-memory cache invalidation is disabled (`settings.in-memory-cache.notification-endpoints-enabled` config property).

This endpoint can be used for invalidating of account settings cache. Responds with empty body.

### Query Params

- `account` - account ID to clear the cache.
