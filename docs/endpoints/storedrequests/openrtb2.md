# Openrtb2

Unavailable if notification is disabled (`settings.in-memory-cache.notification-endpoints-enabled` config property).

The goal is to update/invalidate stored request/impression in-memory caches.

For updating `POST` HTTP method must be used and for invalidating `DELETE` HTTP method.

Possible HTTP requests examples described below.

1. Update in-memory cache for specified stored request and stored impression:

`POST /storedrequests/openrtb2`

```json
{
  "requests": {
    "stored-request-id": "{... stored request data}"
  },
  "imps": {
    "stored-imp-id": "{... stored imp data}"
  }
}
```

2. Invalidate in-memory cache for specified stored request and stored impression:

`DELETE /storedrequests/openrtb2`

```json
{
  "requests": [
    "stored-request-id"
  ],
  "imps": [
    "stored-imp-id"
  ]
}
```

The successive response for both requests will be `200 OK` with empty body.
In case of error while parsing request body `400 Bad Request` with corresponding error message in body will be returned.
