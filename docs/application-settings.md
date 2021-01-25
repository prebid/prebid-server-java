# Application Settings
There are two ways to configure application settings: database and file. This document will describe both approaches.

## Account properties
- `id` - identifies publisher account.
- `price-granularity` - defines price granularity types: 'low','med','high','auto','dense','unknown'.
- `banner-cache-ttl` - how long (in seconds) banner will be available via the external Cache Service.
- `video-cache-ttl`- how long (in seconds) video creative will be available via the external Cache Service.
- `events-enabled` - enables events for account if true
- `enforce-ccpa` - enforces ccpa if true. Has higher priority than configuration in application.yaml.
- `gdpr.enabled` - enables gdpr verifications if true. Has higher priority than configuration in application.yaml.
- `gdpr.integration-enabled.web` - overrides `gdpr.enabled` property behaviour for web requests type.
- `gdpr.integration-enabled.amp` - overrides `gdpr.enabled` property behaviour for amp requests type.
- `gdpr.integration-enabled.app` - overrides `gdpr.enabled` property behaviour for app requests type.
- `gdpr.integration-enabled.video` - overrides `gdpr.enabled` property behaviour for video requests type.
- `gdpr.purposes.[p1-p10].enforce-purpose` - define type of enforcement confirmation: `no`/`basic`/`full`. Default `full`
- `gdpr.purposes.[p1-p10].enforce-vendors` - if equals to `true`, user must give consent to use vendors. Purposes will be omitted. Default `true`
- `gdpr.purposes.[p1-p10].vendor-exceptions[]` - bidder names that will be treated opposite to `pN.enforce-vendors` value.
- `gdpr.special-features.[f1-f2].enforce`- if equals to `true`, special feature will be enforced for purpose. Default `true`
- `gdpr.special-features.[f1-f2].vendor-exceptions` - bidder names that will be treated opposite to `sfN.enforce` value.
- `gdpr.purpose-one-treatment-interpretation` - option that allows to skip the Purpose one enforcement workflow. Values: ignore, no-access-allowed, access-allowed.
- `analytics-sampling-factor` - Analytics sampling factor value. 
- `truncate-target-attr` - Maximum targeting attributes size. Values between 1 and 255.
- `default-integration` - Default integration to assume.
- `analytics-config.auction-events.<channel>` - defines which channels are supported by analytics for this account
- `bid-validations.banner-creative-max-size` - Overrides creative max size validation for banners.
- `status` - allows to mark account as `active` or `inactive`.

Here are the definitions of the "purposes" that can be defined in the GDPR setting configurations:
```
Purpose   | Purpose goal                    | Purpose meaning for PBS (n\a - not affected)  
----------|---------------------------------|---------------------------------------------
p1        | Access device                   | Stops usersync for given vendor and stops settings cookie on `/seuid`
p2        | Select basic ads                | Verify consent for each vendor as appropriate for the enforcement method before calling a bid adapter. If consent is not granted, log a metric and skip it.
p3        | Personalized ads profile        | n\a
p4        | Select personalized ads         | Verify consent for each vendor that passed the Purpose 2. If consent is not granted, remove the bidrequest.userId, user.ext.eids, device.if attributes and call the adapter.
p5        | Personalized content profile    | n\a
p6        | Select personalized content     | n\a
p7        | Measure ad performance          | Verify consent for each analytics module. If consent is not grantet skip it.
p8        | Measure content performance     | n\a
p9        | Generate audience insights      | n\a
p10       | Develop/improve products        | n\a

sf1       | Precise geo                     | Verifies user opt-in. If the user has opted out, rounds off the IP address and lat/long details 
sf2       | Fingerprinting                  | n\a
```

## Setting Account Configuration in Files

In file based approach all configuration stores in .yaml files, path to which are defined in application properties.

### Configuration in application.yaml

The general idea is that you'll place all the account-specific settings in a separate YAML file and point to that file.

```yaml
settings:
  filesystem:
    settings-filename: <directory to yaml file with settings>
```
### File format

Here's an example YAML file containing account-specific settings:

```yaml
accounts:
  - id: 1111
    bannerCacheTtl: 100
    videoCacheTtl: 100
    eventsEnabled: true
    priceGranularity: low
    enforceCcpa: true
    analyticsSamplingFactor: 1
    truncateTargetAttr: 40
    defaultIntegration: web
    analytics-config:
      auction-events:
        amp: true
    status: active
    gdpr:
      enabled: true
      integration-enabled:
        video: true
        web: true
        app: true
        amp: true
      purposes:
        p1:
          enforce-purpose: full
          enforce-vendors: true
          vendor-exceptions:
            - bidder1
            - bidder2
        p2:
          enforce-purpose: full
          enforce-vendors: true
          vendor-exceptions:
            - bidder1
            - bidder2
        p3:
          enforce-purpose: full
          enforce-vendors: true
          vendor-exceptions:
            - bidder1
            - bidder2
        p4:
          enforce-purpose: full
          enforce-vendors: true
          vendor-exceptions:
            - bidder1
            - bidder2
        p5:
          enforce-purpose: full
          enforce-vendors: true
          vendor-exceptions:
            - bidder1
            - bidder2
        p6:
          enforce-purpose: full
          enforce-vendors: true
          vendor-exceptions:
            - bidder1
            - bidder2
        p7:
          enforce-purpose: full
          enforce-vendors: true
          vendor-exceptions:
            - bidder1
            - bidder2
        p8:
          enforce-purpose: full
          enforce-vendors: true
          vendor-exceptions:
            - bidder1
            - bidder2
        p9:
          enforce-purpose: full
          enforce-vendors: true
          vendor-exceptions:
            - bidder1
            - bidder2
        p10:
          enforce-purpose: full
          enforce-vendors: true
          vendor-exceptions:
            - bidder1
            - bidder2
      special-features:
        sf1:
          enforce: true
          vendor-exceptions:
            - bidder1
            - bidder2
        sf2:
          enforce: true
          vendor-exceptions:
            - bidder1
            - bidder2
      purpose-one-treatment-interpretation: ignore
```

## Setting Account Configuration in the Database

In database approach account properties are stored in database table.

SQL query for retrieving account is configurable and can be specified in [application configuration](config-app.md). 
Requirements for the SQL query stated below.

### Configuration in application.yaml

```yaml
settings:
  database:
    pool-size: 20
    type: <mysql or postgres>
    host: <host>
    port: <port>
    account-query: <SQL query for account>
```

### Configurable SQL query for account requirements

The general approach is that each host company can set up their database however they wish, so long as the configurable query run by
Prebid Server returns expected data in the expected order. Here's an example configuration:

```yaml
settings:
  database:
    type: mysql
    account-query: SELECT uuid, price_granularity, banner_cache_ttl, video_cache_ttl, events_enabled, enforce_ccpa, tcf_config, analytics_sampling_factor, truncate_target_attr, default_integration, analytics_config, bid_validations, status FROM accounts_account where uuid = ? LIMIT 1
```

The SQL query for account must:
* return following columns, with specified type, in this order:
    * account ID, string
    * price granularity, string
    * banner cache TTL, integer
    * video cache TTL, integer
    * events enabled flag, boolean
    * enforce CCPA flag, boolean
    * TCF configuration, JSON string, see below
    * analytics sampling factor, integer
    * maximum targeting attribute size, integer
    * default integration value, string
    * analytics configuration, JSON string, see below
    * status, string. Expected values: "active", "inactive", NULL. Only "inactive" has any effect and only when settings.enforce-valid-account is on.
* specify a special single `%ACCOUNT_ID%` placeholder in the `WHERE` clause that will be replaced with account ID in 
runtime

It is recommended to include `LIMIT 1` clause in the query because only the very first result returned will be taken.

If a host company doesn't support a given field, or they have a different table name, they can just update the query with whatever values are needed. e.g.

```yaml
settings:
  database:
    type: mysql
    account-query: SELECT uuid, 'med', banner_cache_ttl, video_cache_ttl, events_enabled, enforce_ccpa, tcf_config, 0, null, default_integration, '{}', '{}' FROM myaccountstable where uuid = ? LIMIT 1
```
### Configuration Details

#### TCF configuration JSON

Here's an example of the value that the `tcf_config` column can take:

```json
{
  "enabled": true,
   "integration-enabled": {
      "video": true,
      "web": true,
      "app": true,
      "amp": true
   },
  "purpose-one-treatment-interpretation": "ignore",
  "purposes": {
    "p1": {
      "enforce-purpose": "full",
      "enforce-vendors": true,
      "vendor-exceptions": [
        "bidder1",
        "bidder2"
      ]
    },
    "p2": {
      "enforce-purpose": "full",
      "enforce-vendors": true,
      "vendor-exceptions": [
        "bidder1",
        "bidder2"
      ]
    },
    "p3": {
      "enforce-purpose": "full",
      "enforce-vendors": true,
      "vendor-exceptions": [
        "bidder1",
        "bidder2"
      ]
    },
    "p4": {
      "enforce-purpose": "full",
      "enforce-vendors": true,
      "vendor-exceptions": [
        "bidder1",
        "bidder2"
      ]
    },
    "p5": {
      "enforce-purpose": "full",
      "enforce-vendors": true,
      "vendor-exceptions": [
        "bidder1",
        "bidder2"
      ]
    },
    "p6": {
      "enforce-purpose": "full",
      "enforce-vendors": true,
      "vendor-exceptions": [
        "bidder1",
        "bidder2"
      ]
    },
    "p7": {
      "enforce-purpose": "full",
      "enforce-vendors": true,
      "vendor-exceptions": [
        "bidder1",
        "bidder2"
      ]
    },
    "p8": {
      "enforce-purpose": "full",
      "enforce-vendors": true,
      "vendor-exceptions": [
        "bidder1",
        "bidder2"
      ]
    },
    "p9": {
      "enforce-purpose": "full",
      "enforce-vendors": true,
      "vendor-exceptions": [
        "bidder1",
        "bidder2"
      ]
    },
    "p10": {
      "enforce-purpose": "full",
      "enforce-vendors": true,
      "vendor-exceptions": [
        "bidder1",
        "bidder2"
      ]
    }
  },
  "special-features": {
    "sf1": {
      "enforce": true,
      "vendor-exceptions": [
        "bidder1",
        "bidder2"
      ]
    },
    "sf2": {
      "enforce": true,
      "vendor-exceptions": [
        "bidder1",
        "bidder2"
      ]
    }
  }
}
```

#### Bid Validations configuration JSON

The `bid_validations` column is json with this format:

```json
{
  "banner-creative-max-size": "enforce"
}
```

Valid values are:
- "skip": don't do anything about creative max size for this publisher
- "warn": if a bidder returns a creative that's larger in height or width than any of the allowed sizes, log an operational warning.
- "enforce": if a bidder returns a creative that's larger in height or width than any of the allowed sizes, reject the bid and log an operational warning.

#### Analytics Validations configuration JSON

The `analytics_config`  configuration column format:

```json
{
  "auction-events": {
    "web": true,   // the analytics adapter should log auction events when the channel is web
    "amp": true,   // the analytics adapter should log auction events when the channel is AMP
    "app": false   // the analytics adapter should not log auction events when the channel is app
  }
}
```

#### Creating the accounts table

Traditionally the table name used by Prebid Server is `accounts_account`. No one remembers why. But here's SQL 
you could use to create your table:

```sql
'CREATE TABLE `accounts_account` (
    `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
    `uuid` varchar(40) NOT NULL,
    `price_granularity` enum('low','med','high','auto','dense','unknown') NOT NULL DEFAULT 'unknown',
    `granularityMultiplier` decimal(9,3) DEFAULT NULL,
    `banner_cache_ttl` int(11) DEFAULT NULL,
    `video_cache_ttl` int(11) DEFAULT NULL,
    `events_enabled` bit(1) DEFAULT NULL,
    `enforce_ccpa` bit(1) DEFAULT NULL,
    `enforce_gdpr` bit(1) DEFAULT NULL,
    `tcf_config` json DEFAULT NULL,
    `analytics_sampling_factor` tinyint(4) DEFAULT NULL,
    `truncate_target_attr` tinyint(3) unsigned DEFAULT NULL,
    `default_integration` varchar(64) DEFAULT NULL,
    `analytics_config` varchar(512) DEFAULT NULL,
    `bid_validations` json DEFAULT NULL,
    `status` enum('active','inactive') DEFAULT 'active',
    `updated_by` int(11) DEFAULT NULL,
    `updated_by_user` varchar(64) DEFAULT NULL,
    `updated` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
PRIMARY KEY (`id`),
UNIQUE KEY `uuid` (`uuid`))
ENGINE=InnoDB DEFAULT CHARSET=utf8'
```
