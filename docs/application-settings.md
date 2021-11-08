# Application Settings

There are two ways to configure application settings: database and file. This document will describe both approaches.

## Account properties

- `id` - identifies publisher account.
- `status` - allows to mark account as `active` or `inactive`.
- `auction.price-granularity` - defines price granularity types: 'low','med','high','auto','dense','unknown'.
- `auction.banner-cache-ttl` - how long (in seconds) banner will be available via the external Cache Service.
- `auction.video-cache-ttl`- how long (in seconds) video creative will be available via the external Cache Service.
- `auction.truncate-target-attr` - Maximum targeting attributes size. Values between 1 and 255.
- `auction.default-integration` - Default integration to assume.
- `auction.bid-validations.banner-creative-max-size` - Overrides creative max size validation for banners. Valid values
  are:
    - "skip": don't do anything about creative max size for this publisher
    - "warn": if a bidder returns a creative that's larger in height or width than any of the allowed sizes, log an
      operational warning.
    - "enforce": if a bidder returns a creative that's larger in height or width than any of the allowed sizes, reject
      the bid and log an operational warning.
- `auction.events.enabled` - enables events for account if true
- `privacy.ccpa.enabled` - enables gdpr verifications if true. Has higher priority than configuration in application.yaml.
- `privacy.ccpa.integration-enabled.web` - overrides `ccpa.enforce` property behaviour for web requests type.
- `privacy.ccpa.integration-enabled.amp` - overrides `ccpa.enforce` property behaviour for amp requests type.
- `privacy.ccpa.integration-enabled.app` - overrides `ccpa.enforce` property behaviour for app requests type.
- `privacy.ccpa.integration-enabled.video` - overrides `ccpa.enforce` property behaviour for video requests type.
- `privacy.gdpr.enabled` - enables gdpr verifications if true. Has higher priority than configuration in
  application.yaml.
- `privacy.gdpr.integration-enabled.web` - overrides `privacy.gdpr.enabled` property behaviour for web requests type.
- `privacy.gdpr.integration-enabled.amp` - overrides `privacy.gdpr.enabled` property behaviour for amp requests type.
- `privacy.gdpr.integration-enabled.app` - overrides `privacy.gdpr.enabled` property behaviour for app requests type.
- `privacy.gdpr.integration-enabled.video` - overrides `privacy.gdpr.enabled` property behaviour for video requests
  type.
- `privacy.gdpr.purposes.[p1-p10].enforce-purpose` - define type of enforcement confirmation: `no`/`basic`/`full`.
  Default `full`
- `privacy.gdpr.purposes.[p1-p10].enforce-vendors` - if equals to `true`, user must give consent to use vendors.
  Purposes will be omitted. Default `true`
- `privacy.gdpr.purposes.[p1-p10].vendor-exceptions[]` - bidder names that will be treated opposite
  to `pN.enforce-vendors` value.
- `privacy.gdpr.special-features.[f1-f2].enforce`- if equals to `true`, special feature will be enforced for purpose.
  Default `true`
- `privacy.gdpr.special-features.[f1-f2].vendor-exceptions` - bidder names that will be treated opposite
  to `sfN.enforce` value.
- `privacy.gdpr.purpose-one-treatment-interpretation` - option that allows to skip the Purpose one enforcement workflow.
  Values: ignore, no-access-allowed, access-allowed.
- `analytics.auction-events.<channel>` - defines which channels are supported by analytics for this account
- `analytics.modules.<module-name>.*` - space for `module-name` analytics module specific configuration, may be of any shape
- `cookie-sync.default-limit` - if the "limit" isn't specified in the `/cookie_sync` request, this is what to use
- `cookie-sync.max-limit` - if the "limit" is specified in the `/cookie_sync` request, it can't be greater than this
  value
- `cookie-sync.default-coop-sync` - if the "coopSync" value isn't specified in the `/cookie_sync` request, use this

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
      status: active
      auction:
        price-granularity: low
        banner-cache-ttl: 100
        video-cache-ttl: 100
        truncate-target-attr: 40
        default-integration: web
        bid-validations:
          banner-creative-max-size: enforce
        events:
          enabled: true
      privacy:
        ccpa:
          enabled: true
          integration-enabled:
            video: true
            web: true
            app: true
            amp: true
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
      analytics:
        auction-events:
          amp: true
      cookie-sync:
        default-limit: 5
        max-limit: 8
        default-coop-sync: true
```

## Setting Account Configuration in the Database

In database approach account properties are stored in database table(s).

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

The general approach is that each host company can set up their database however they wish, so long as the configurable
query run by Prebid Server returns expected data in the expected order. Here's an example configuration:

```yaml
settings:
  database:
    type: mysql
    account-query: SELECT config FROM accounts_account where uuid = ? LIMIT 1
```

The SQL query for account must:

* return following columns, with specified type, in this order:
    * configuration document, JSON string, see below
* specify a special single `%ACCOUNT_ID%` placeholder in the `WHERE` clause that will be replaced with account ID in
  runtime

It is recommended to include `LIMIT 1` clause in the query because only the very first result returned will be taken.

### Configuration document JSON

The configuration document JSON returned by the SQL query must conform to the format illustrated with the following
example:

```json
{
  "id": "1111",
  "status": "active",
  "auction": {
    "price-granularity": "low",
    "banner-cache-ttl": 100,
    "video-cache-ttl": 100,
    "truncate-target-attr": 40,
    "default-integration": "web",
    "bid-validations": {
      "banner-creative-max-size": "enforce"
    },
    "events": {
      "enabled": true
    }
  },
  "privacy": {
    "ccpa": {
      "enabled": true,
      "integration-enabled": {
          "web": true,
          "amp": false,
          "app": true,
          "video": false
      }
    },
    "gdpr": {
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
  },
  "analytics": {
    "auction-events": {
      // the analytics adapter should log auction events when the channel is web
      "web": true,
      // the analytics adapter should log auction events when the channel is AMP
      "amp": true,
      // the analytics adapter should not log auction events when the channel is app
      "app": false
    }
  },
  "cookie-sync": {
    "default-limit": 5,
    "max-limit": 8,
    "default-coop-sync": true
  }
}
```

At some point this format might be formalized into an
exhaustive [JSON Schema](https://json-schema.org/specification.html).

#### SQL Query example

Prebid Server does not impose any rules for the table(s) schema but requires SQL query specified in 
configuration to return a single column of JSON type containing the document adhering to the format shown above.

It might be the case that the host companies store account configuration in a table of arbitrary structure or even in
several tables. MySQL and Postgres provides necessary functions allowing to project practically any table structure to 
expected JSON format.

Let's assume following table schema for example:
```mysql-sql
'CREATE TABLE `accounts_account` (
    `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
    `uuid` varchar(40) NOT NULL,
    `price_granularity` enum('low','med','high','auto','dense','unknown') NOT NULL DEFAULT 'unknown',
    `banner_cache_ttl` int(11) DEFAULT NULL,
    `video_cache_ttl` int(11) DEFAULT NULL,
    `events_enabled` bit(1) DEFAULT NULL,
    `enforce_ccpa` bit(1) DEFAULT NULL,
    `tcf_config` json DEFAULT NULL,
    `truncate_target_attr` tinyint(3) unsigned DEFAULT NULL,
    `default_integration` varchar(64) DEFAULT NULL,
    `analytics_config` json DEFAULT NULL,
    `bid_validations` json DEFAULT NULL,
    `config` json DEFAULT NULL,
    `status` enum('active','inactive') DEFAULT 'active',
    `updated_by` int(11) DEFAULT NULL,
    `updated_by_user` varchar(64) DEFAULT NULL,
    `updated` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
PRIMARY KEY (`id`),
UNIQUE KEY `uuid` (`uuid`))
ENGINE=InnoDB DEFAULT CHARSET=utf8'
```

The following Mysql SQL query could be used to construct a JSON document of required shape on the fly:

```mysql-sql
SELECT JSON_MERGE_PATCH(
               JSON_OBJECT(
                       'id', uuid,
                       'status', status,
                       'auction', JSON_OBJECT(
                               'price-granularity', price_granularity,
                               'banner-cache-ttl', banner_cache_ttl,
                               'video-cache-ttl', video_cache_ttl,
                               'truncate-target-attr', truncate_target_attr,
                               'default-integration', default_integration,
                               'bid-validations', bid_validations,
                               'events', JSON_OBJECT('enabled', NOT NOT (events_enabled))
                           ),
                       'privacy', JSON_OBJECT(
                               'ccpa', JSON_OBJECT('enabled', NOT NOT (enforce_ccpa)),
                               'gdpr', tcf_config
                           ),
                       'analytics', analytics_config
                   ),
               COALESCE(config, '{}')) as consolidated_config
FROM accounts_account
WHERE uuid = %ACCOUNT_ID%
LIMIT 1
```
