# ID5 User ID Module

This module integrates ID5's universal identifier service into Prebid Server Java, enabling publishers to fetch and inject ID5 user IDs into bid requests sent to bidders.

## Quick Navigation

- **[Production Setup](#production-setup)** - For publishers and PBS operators
- **[Module Development](#module-development)** - For developers working on the module

---

# Production Setup

## Overview

The ID5 User ID module fetches identity signals from ID5's API and automatically injects them into OpenRTB bid requests as Extended Identifiers (EIDs). This enhances user matching for participating bidders while respecting user privacy preferences.

## Features

- Fetches ID5 universal identifiers and automatically injects them into bid requests as Extended Identifiers (EIDs)
- Only adds ID5 when not already present in the publisher's bid request - preserves existing ID5 identifiers
- Privacy-compliant: respects GDPR, CCPA, COPPA, and GPP signals
- Flexible control: filter by account, country, bidder, or use sampling to gradually roll out

## How It Works

The module uses two hooks in the Prebid Server request lifecycle:

1. **Fetch Hook** (`ProcessedAuctionRequestHook`):
   - Triggered early in the auction request processing
   - **First checks if ID5 EID already exists** - if present, skips fetching entirely
   - Initiates an asynchronous call to ID5's API only when ID5 is not present
   - Stores the Future result in module context for later use
   - Applies fetch filters (account, country, sampling)

2. **Inject Hook** (`BidderRequestHook`):
   - Triggered before each bidder request
   - **Checks again if ID5 EID is already present** - if so, skips injection
   - Awaits the ID5 fetch result (with timeout awareness)
   - Injects ID5 EIDs into `user.eids` field only when not already present
   - Applies inject filters (bidder selection)
   - Sets the `inserter` field to the EID if configured

```
┌─────────────────┐
│ Auction Request │
└────────┬────────┘
         │
         v
┌─────────────────────────┐
│ ID5 Fetch Hook          │
│ Check: ID5 exists?      │
└────────┬────────────────┘
         │
         ├─ YES ──> Skip (no fetch needed)
         │
         └─ NO ───> Async call to ID5 API
                    stores Future in context
         │
         v
┌─────────────────────┐
│ Bidder Requests     │
└────────┬────────────┘
         │
         v
┌─────────────────────────┐
│ ID5 Inject Hook         │
│ Check: ID5 exists?      │
└────────┬────────────────┘
         │
         ├─ YES ──> Skip (preserve existing)
         │
         └─ NO ───> Await Future, inject EIDs
```
## What Data is Sent to ID5

The module sends the following information to ID5's API:

**From Bid Request:**
- App bundle (`app.bundle`)
- Site domain (`site.domain`)
- Site referrer (`site.ref`)
- Device IFA/MAID (`device.ifa`)
- Device User Agent (`device.ua`)
- Device IP address (`device.ip`)
- ATT status (`device.ext.atts`)

**Privacy Signals:**
- GDPR consent string
- GDPR applies flag
- US Privacy (CCPA) string
- COPPA flag
- GPP string and SID

**Module Metadata:**
- Partner ID
- Timestamp
- PBS version
- Origin identifier
- Provider string

## What data is added to the bidder request?

The module's `ID5 Inject Hook`  adds [EID](https://github.com/InteractiveAdvertisingBureau/openrtb2.x/blob/main/2.6.md#3227---object-eid-)s to the OpenRTB `user.eids` array.   
The EID objects come from a response to the fetch request triggered by `ID5 Fetch Hook` called at earlier stage of the action.  
The EID before insertion can be enriched with `inserter` field which is configurable by server host.

Example EID added:

```json
{
  "user": {
    "eids": [
      {
        "source": "id5-sync.com",
        "uids": [
          {
            "id": "ID5*YsvxY...",
            "atype": 1,
            "ext": {
              "linkType": 2,
              "pba": "jWwv+..."
            }
          }
        ],
        "inserter": "pbs-company.com" // this can be configured 
      }
    ]
  }
}
```

## Privacy & Compliance

The module respects privacy signals:

- **GDPR**: Passes consent string and GDPR applies flag to ID5
- **CCPA**: Passes US Privacy string to ID5
- **COPPA**: Passes COPPA flag to ID5
- **GPP**: Passes GPP string and applicable sections to ID5

ID5's API will respect these signals when generating identifiers. Ensure your privacy policy covers the use of ID5's service and data sharing with ID5.


## Configuration

### Required Properties

| Property | Type | Description                                                                                                                                                                                          |
|----------|------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `enabled` | boolean | Must be `true` to activate the module                                                                                                                                                                |
| `providerName` | string | Provider identifier string sent to ID5 API. Identifies who is hosting/operating the Prebid Server instance (e.g., "my-company-pbs", "my-company-com").                                                |
| `partner` | long | ID5 Partner ID (minimum value: 1). Required only when using the default constant provider. Not needed if you provide a custom `Id5PartnerIdProvider` bean (see Custom Partner ID Configuration below) |

### Custom Partner ID Configuration

By default, the module uses a constant Partner ID from the `hooks.id5-user-id.partner` configuration property. This partner id is used for each id5id fetch request.

In some configurations may be needed to pass different partner depending on channel, publisher where the auction request comes from, or anything else.
For such cases implement the `Id5PartnerIdProvider` interface and register it as a Spring bean. The module will automatically use your custom implementation.

**Important:** If the provider returns an empty value, the ID5 fetch will be skipped for that request.

### Optional Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `inserterName` | string | null | The canonical domain name of the entity that caused this EID to be added (e.g., "pbs-company.com", "ssp.example.com"). Should be the operational domain of the system running this module. See [OpenRTB EID specification](https://github.com/InteractiveAdvertisingBureau/openrtb2.x/blob/main/2.6.md#3227---object-eid-) for details. |
| `fetchEndpoint` | string | `https://api.id5-sync.com/gs/v2` | ID5 API endpoint URL |
| `fetchSamplingRate` | double | 1.0 | Percentage of requests to sample (0.0-1.0) |
| `bidderFilter` | ValuesFilter | null | Filter bidders that receive IDs |
| `accountFilter` | ValuesFilter | null | Filter accounts that trigger fetches |
| `countryFilter` | ValuesFilter | null | Filter countries that trigger fetches |

### ValuesFilter Structure

Each filter supports include/exclude semantics:

```yaml
<filterName>:
  exclude: false  # false = allowlist, true = blocklist
  values:         # list of values to include/exclude
    - value1
    - value2
```

**Allowlist mode** (`exclude: false`):
- Only requests matching the listed values will proceed
- Empty or null values list = allow all

**Blocklist mode** (`exclude: true`):
- Requests matching the listed values will be rejected
- Empty or null values list = allow all

### Filter Behavior

Filters are evaluated in sequence. If any filter rejects the request, no ID5 fetch occurs:

1. **Sampling Filter**: Random sampling based on `fetchSamplingRate`
2. **Account Filter**: Checks account ID against `accountFilter`
3. **Country Filter**: Checks country code against `countryFilter`
4. **Bidder Filter**: (Inject only) Checks bidder name against `bidderFilter`

## Integration

### Add module dependency 
The module dependency must be added to your server application. 
```xml
<dependency>
    <groupId>org.prebid.server.hooks.modules</groupId>
    <artifactId>id5-user-id</artifactId>
    <version>${PREBID_SERVER_VERSION}</version> <!-- used PBS version -->
</dependency>
```

The module is included in the `extra/bundle` by default `extra/bundle/pom.xml`

### Configure module

To run a module you must
- enable module in config and add required properties
- configure an execution plan that registers the module's hooks. See [Configure an execution plan with module's hooks](#configure-an-execution-plan-with-modules-hooks) for details.

#### Enabling and configuring module
##### Basic (minimal) Setup
Enable for all accounts and bidders:
```yaml
hooks:
  id5-user-id:
    enabled: true
    provider-name: "my-pbs-host"  # Required: identifies who operates this PBS instance
    partner: 173
```

##### Gradual Rollout with Sampling
```yaml
hooks:
  id5-user-id:
    enabled: true
    provider-name: "my-pbs-host"  # Required: identifies who operates this PBS instance
    partner: 173
    fetch-sampling-rate: 0.1  # 10% of requests
```

##### With multiple filters
```yaml
hooks:
  id5-user-id:
    enabled: true
    provider-name: "my-pbs-host"  # Required: identifies who operates this PBS instance
    partner: 173
    inserter-name: "pbs-company.com"  # Canonical domain of the entity that added this EID
    fetch-sampling-rate: 0.8
    account-filter: # for auctions from any account except "test-account"  
      exclude: true
      values: [test-account]
    country-filter: # only actions from listed countries
      exclude: false
      values: [US, GB, DE, FR]
    bidder-filter: # id will be added to only listed bidder's requests
    exclude: false
    values: [rubicon, appnexus, pubmatic]
```

#### Configure an execution plan with module's hooks
Default or for a specific account.

```yaml
accounts:
  - id: "1001"
    status: active
    hooks:
      execution-plan:
        {
          "endpoints": {
            "/openrtb2/auction": {
              "stages": {
                "processed-auction-request": {
                  "groups": [
                    {    
                      "hook-sequence": [
                        { "module-code": "id5-user-id", "hook-impl-code": "id5-user-id-fetch-hook" }
                      ]
                    }
                  ]
                },
                "bidder-request": {
                  "groups": [
                    {  
                      "hook-sequence": [
                        { "module-code": "id5-user-id", "hook-impl-code": "id5-user-id-inject-hook" }
                      ]
                    }
                  ]
                }
              }
            }
          }
        }
```
## Troubleshooting

### No EIDs appearing in bid requests

**Check:**
1. Module dependency is added to your server application's class path
2. Module is enabled: `enabled: true` in configuration
3. Required properties are set (`partner` property or custom `PartnerIdProvider` spring bean, `provider-name` property)
4. Execution plan configured with module's hooks is configured
5. Enable DEBUG logging and verify fetch/inject occurred
6. Verify filters aren't excluding the request

## Performance Considerations

- ID5 fetch is asynchronous and non-blocking
- Fetch result is cached in module context for all bidders
- HTTP client uses connection pooling
- Timeout is respected from the auction context
- Failed fetches don't block the auction (returns empty)

## Support

For issues specific to:
- **Module implementation**: Contact ID5 support at support@id5.io
- **ID5 service/API**: Contact ID5 support at support@id5.io
- **Partner ID registration**: Contact your ID5 account manager

---

# Module Development

This section is for developers working on the ID5 User ID module itself.

## Debugging

Enable debug mode to see detailed hook execution messages:

```yaml
logging:
  level:
    org.prebid.server.hooks.modules.id5: DEBUG
```

Debug logs will show when IDs are fetched, injected, or skipped (due to filters, existing IDs, or timeouts).

## Local End-to-End Testing

The repository includes complete sample configurations for local testing with WireMock mocks. This allows you to test the full ID5 module flow without connecting to the real ID5 API.

### Prerequisites

- Java 17+
- Maven 3.8+
- Docker Desktop (for WireMock)

### Quick Start

1. **Start WireMock**:
   ```bash
   cd sample/wiremock
   docker compose -f docker-compose.wiremock.yml up -d
   ```

2. **Build and run PBS with ID5 module**:
   From the project root directory:
   ```bash
   cd extra
   mvn clean package -pl bundle -am -DskipTests
   cd ..
   java -jar extra/bundle/target/prebid-server-bundle.jar --spring.config.additional-location=sample/configs/prebid-config-with-id5.yaml
   ```

3. **Send test request**:
   ```bash
   curl -X POST http://localhost:8080/openrtb2/auction \
     -H "Content-Type: application/json" \
     -d @sample/requests/localdev-test-request.http
   ```

4. **Verify**: Check logs for `id5-user-id-fetch: id5id fetched` and `id5-user-id-inject: updated user with id5 eids`

### Configuration Files Reference

| File | Purpose |
|------|---------|
| `sample/configs/prebid-config-with-id5.yaml` | Main PBS config with ID5 module enabled |
| `sample/configs/sample-app-settings-id5.yaml` | Account settings with hooks execution plan |
| `sample/wiremock/mappings/id5-fetch.json` | WireMock mapping for ID5 API |
| `sample/wiremock/__files/id5-fetch-response.json` | Mock ID5 API response |
| `sample/requests/localdev-test-request.http` | Sample auction request |

### Testing Different Scenarios

Test various behaviors by modifying the configuration or WireMock mappings:
- **Control test**: Change account ID to one without hooks configured - verify no EIDs added
- **Timeout behavior**: Add `fixedDelayMilliseconds` to WireMock response
- **Error handling**: Change WireMock to return HTTP 503
- **Filter testing**: Add bidder/account/country filters to configuration

## Unit Tests

Run the comprehensive unit test suite:

```bash
# From the module directory
cd extra/modules/id5-user-id

# Run all unit tests (excludes *IT.java integration tests)
mvn test

# Run specific test class
mvn test -Dtest=Id5IdFetchHookTest

# Run with debug logging
mvn test -Dorg.slf4j.simpleLogger.defaultLogLevel=debug
```

## Integration Tests

The module includes integration tests (`*IT.java`) that run a full Prebid Server instance with the ID5 module enabled.

**Prerequisites**:
1. Build and install the main project with test-jar:
   ```bash
   cd /path/to/prebid-server-java
   mvn clean install -DskipUnitTests=true -DskipITs=true
   ```

**Run integration tests**:
```bash
cd extra/modules/id5-user-id

# Run all integration tests
mvn verify

# Run specific integration test
mvn verify -Dit.test=Id5UserIdModuleIT

# Skip integration tests (run only unit tests)
mvn test
```

---

## Version History

- **v1.0**: Initial implementation
  - Fetch and inject hooks
  - Configurable filtering (account, country, bidder, sampling)
  - Privacy signal support (GDPR, CCPA, COPPA, GPP)

## License

This module is part of Prebid Server Java and follows the same license terms.
