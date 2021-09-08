# Deals

## Planner and Register services

### Planner service

Periodically request Line Item metadata from the Planner. Line Item metadata includes:
1. Line Item details
2. Targeting
3. Frequency caps
4. Delivery schedule

### Register service

Each Prebid Server instance register itself with the General Planner with a health index
(QoS indicator based on its internal counters like circuit breaker trip counters, timeouts, etc.)
and KPI like ad requests per second.

Also allows planner send command to PBS admin endpoint to stored request caches and tracelogs.

### Planner and register service configuration 

```yaml
planner:
   register-endpoint: <register endpoint>
   plan-endpoint: <planner endpoint>
   update-period: "0 */1 * * * *"
   register-period-sec: 60
   timeout-ms: 8000
   username: <username for BasicAuth>
   password: <password for BasicAuth>
```

## Deals stats service

Supports sending reports to delivery stats serving with following metrics:

1. Number of client requests seen since start-up
2. For each Line Item
- Number of tokens spent so far at each token class within active and expired plans
- Number of times the account made requests (this will be the same across all LineItem for the account)
- Number of win notifications
- Number of times the domain part of the target matched
- Number of times impressions matched whole target
- Number of times impressions matched the target but was frequency capped
- Number of times impressions matched the target but the fcap lookup failed
- Number of times LineItem was sent to the bidder
- Number of times LineItem was sent to the bidder as the top match
- Number of times LineItem came back from the bidder
- Number of times the LineItem response was invalidated 
- Number of times the LineItem was sent to the client
- Number of times the LineItem was sent to the client as the top match
- Array of top 10 competing LineItems sent to client

### Deals stats service configuration

```yaml
delivery-stats:
  endpoint: <delivery stats endpoint>
  delivery-period: "0 */1 * * * *"
  cached-reports-number: 20
  line-item-status-ttl-sec: 3600
  timeout-ms: 8000
  username: <username for BasicAuth>
  password: <password for BasicAuth>
```

## Alert service

Sends out alerts when PBS cannot talk to general planner and other critical situations. Alerts are simply JSON messages
over HTTP sent to a central proxy server.

```yaml
  alert-proxy:
    enabled: truew
    timeout-sec: 10
    url: <aler service endpoint url>
    username: <username for BasicAuth>
    password: <password for BasicAuth>
    alert-types: 
       <type of allert>: <sampling factor>
       pbs-planner-empty-response-error: 15
```

## GeoLocation service

This service currently has 1 implementation:
- MaxMind

In order to support targeting by geographical attributes the service will provide the following information:

1. `continent` - Continent code
2. `region` - Region code using ISO-3166-2
3. `metro` - Nielsen DMAs
4. `city` - city using provider specific encoding
5. `lat` - latitude from -90.0 to +90.0, where negative is south
6. `lon` - longitude from -180.0 to +180.0, where negative is west

### GeoLocation service configuration for MaxMind

```yaml
geolocation:
  enabled: true
  type: maxmind
  maxmind:
    remote-file-syncer:
      download-url: <url to maxmind database>
      save-filepath: <save-filepath>
      tmp-filepath: <tmp-filepath>
      retry-count: 3
      retry-interval-ms: 3000
      timeout-ms: 300000
      update-interval-ms: 0
      http-client:
        connect-timeout-ms: 2500
        max-redirects: 3
```

## User Service

This service is responsible for:
- Requesting user targeting segments and frequency capping status from the User Data Store
- Reporting to User Data Store when users finally see ads to aid in correctly enforcing frequency caps

### User service configuration

```yaml
  user-data:
    win-event-endpoint: <win-url-endpoint>
    user-details-endpoint: <user-deatils-endpoint>
    timeout: 1000
    user-ids:
      - location: rubicon
        source: uid
        type: khaos
```
1. khaos, adnxs - types of the ids that will be specified in requests to User Data Store
2. source - source of the id, the only supported value so far is “uids” which stands for uids cookie
3. location - where exactly in the source to look for id

## Device Info Service

DeviceInfoService returns  device-related attributes based on User-Agent for use in targeting:
- deviceClass: desktop, tablet, phone, ctv
- os: windows, ios, android, osx, unix, chromeos
- osVersion
- browser: chrome, firefox, edge, safari
- browserVersion

## See also

- [Configuration](config.md)
