# Amp

Unavailable if notification is disabled (`settings.in-memory-cache.notification-endpoints-enabled` config property)

This `/pbs-admin/storedrequests/amp` endpoint can be configured:
 - `admin-endpoints.storedrequest-amp.on-application-port` - when equals to `false` endpoint will be bound to `admin.port`.
 - `admin-endpoints.storedrequest-amp.protected` - when equals to `true` endpoint will be protected by basic authentication configured in `admin-endpoints.credentials` 

The goal is to update/invalidate AMP stored request/impression in-memory caches.

The request/response is equivalent to [openrtb2](../openrtb2.md)
