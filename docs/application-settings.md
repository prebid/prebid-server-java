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
- `auction.debug-allow` - enables debug output in the auction response. Default `true`.
- `auction.bid-validations.banner-creative-max-size` - Overrides creative max size validation for banners. Valid values
  are:
    - "skip": don't do anything about creative max size for this publisher
    - "warn": if a bidder returns a creative that's larger in height or width than any of the allowed sizes, log an
      operational warning.
    - "enforce": if a bidder returns a creative that's larger in height or width than any of the allowed sizes, reject
      the bid and log an operational warning.
- `auction.events.enabled` - enables events for account if true
- `auction.price-floors.enabeled` - enables price floors for account if true.
- `auction.price-floors.fetch.enabled`- enables data fetch for price floors for account if true.
- `auction.price-floors.fetch.url` - url to fetch price floors data from.
- `auction.price-floors.fetch.timeout-ms` - timeout for fetching price floors data.
- `auction.price-floors.fetch.max-file-size-kb` - maximum size of price floors data to be fetched.
- `auction.price-floors.fetch.max-rules` - maximum number of rules per model group.
- `auction.price-floors.fetch.max-age-sec` - maximum time that fetched price floors data remains in cache.
- `auction.price-floors.fetch.period-sec` - time between two consecutive fetches.
- `auction.price-floors.enforce-floors-rate` - what percentage of the time a defined floor is enforced. Default is 100.
- `auction.price-floors.adjust-for-bid-adjustment` - boolean for whether to use the bidAdjustment function to adjust the floor per bidder. Defaults to true.
- `auction.price-floors.enforce-deal-floors` - boolean for whether to enforce floors on deals. Defaults to false.
- `auction.price-floors.use-dynamic-data` - boolean that can be used as an emergency override to start ignoring dynamic floors data if something goes wrong. Defaults to true.
- `auction.targeting.includewinners` - whether to include targeting for the winning bids in response. Default `false`.
- `auction.targeting.includebidderkeys` - whether to include targeting for the best bid from each bidder in response. Default `false`.
- `auction.targeting.includeformat` - whether to include the “hb_format” targeting key. Default `false`.
- `auction.targeting.preferdeals` - if targeting is returned and this is `true`, PBS will choose the highest value deal before choosing the highest value non-deal. Default `false`.
- `auction.targeting.alwaysincludedeals` - PBS-Java only. If true, generate `hb_ATTR_BIDDER` values for all bids that have a `dealid`. Default to `false`.
- `auction.targeting.prefix` - defines prefix for targeting keywords. Default `hb`. 
Keep in mind following restrictions:
    - this prefix value may be overridden by correspond property from bid request
    - prefix length is limited by `auction.truncate-target-attr`
    - if custom prefix may produce keywords that exceed `auction.truncate-target-attr`, prefix value will drop to default `hb`
- `auction.preferredmediatype.<bidder>.<media-type>` - <media-type> that will be left for <bidder> that doesn't support multi-format. Other media types will be removed. Acceptable values: `banner`, `video`, `audio`, `native`.
- `auction.privacysandbox.cookiedeprecation.enabled` - boolean that turns on setting and reading of the Chrome Privacy Sandbox testing label header. Defaults to false.
- `auction.privacysandbox.cookiedeprecation.ttlsec` - if the above setting is true, how long to set the receive-cookie-deprecation cookie's expiration
- `privacy.gdpr.enabled` - enables gdpr verifications if true. Has higher priority than configuration in
  application.yaml.
- `privacy.gdpr.eea-countries` - overrides the host-level list of 2-letter country codes where TCF processing is applied
- `privacy.gdpr.channel-enabled.web` - overrides `privacy.gdpr.enabled` property behaviour for web requests type.
- `privacy.gdpr.channel-enabled.amp` - overrides `privacy.gdpr.enabled` property behaviour for amp requests type.
- `privacy.gdpr.channel-enabled.app` - overrides `privacy.gdpr.enabled` property behaviour for app requests type.
- `privacy.gdpr.channel-enabled.video` - overrides `privacy.gdpr.enabled` property behaviour for video requests
  type.
- `privacy.gdpr.channel-enabled.dooh` - overrides `privacy.gdpr.enabled` property behaviour for dooh requests
  type.
- `privacy.gdpr.purposes.[p1-p10].enforce-purpose` - define type of enforcement confirmation: `no`/`basic`/`full`.
  Default `full`
- `privacy.gdpr.purposes.[p1-p10].enforce-vendors` - if equals to `true`, user must give consent to use vendors.
  Purposes will be omitted. Default `true`
- `privacy.gdpr.purposes.[p1-p10].vendor-exceptions[]` - bidder names that will be treated opposite
  to `pN.enforce-vendors` value.
- `privacy.gdpr.purposes.p4.eid.activity_transition` - defaults to `true`. If `true` and transmitEids is not specified, but transmitUfpd is specified, then the logic of transmitUfpd is used. This is to avoid breaking changes to existing configurations. The default value of the flag will be changed in a future release. 
- `privacy.gdpr.purposes.p4.eid.require_consent` - if equals to `true`, transmitting EIDs require P4 legal basis unless excepted.
- `privacy.gdpr.purposes.p4.eid.exceptions` - list of EID sources that are excepted from P4 enforcement and will be transmitted if any P2-P10 is consented.
- `privacy.gdpr.special-features.[sf1-sf2].enforce`- if equals to `true`, special feature will be enforced for purpose.
  Default `true`
- `privacy.gdpr.special-features.[sf1-sf2].vendor-exceptions` - bidder names that will be treated opposite
  to `sfN.enforce` value.
- `privacy.gdpr.purpose-one-treatment-interpretation` - option that allows to skip the Purpose one enforcement workflow.
  Values: ignore, no-access-allowed, access-allowed.
- `privacy.gdpr.basic-enforcement-vendors` - bypass vendor-level checks for these biddercodes.
- `privacy.ccpa.enabled` - enables gdpr verifications if true. Has higher priority than configuration in application.yaml.
- `privacy.ccpa.channel-enabled.web` - overrides `ccpa.enforce` property behaviour for web requests type.
- `privacy.ccpa.channel-enabled.amp` - overrides `ccpa.enforce` property behaviour for amp requests type.
- `privacy.ccpa.channel-enabled.app` - overrides `ccpa.enforce` property behaviour for app requests type.
- `privacy.ccpa.channel-enabled.video` - overrides `ccpa.enforce` property behaviour for video requests type.
- `privacy.ccpa.channel-enabled.dooh` - overrides `ccpa.enforce` property behaviour for dooh requests type.
- `privacy.dsa.default.dsarequired` - inject this dsarequired value for this account. See https://github.com/InteractiveAdvertisingBureau/openrtb/blob/main/extensions/community_extensions/dsa_transparency.md for details.
- `privacy.dsa.default.pubrender` - inject this pubrender value for this account. See https://github.com/InteractiveAdvertisingBureau/openrtb/blob/main/extensions/community_extensions/dsa_transparency.md for details.
- `privacy.dsa.default.datatopub` - inject this datatopub value for this account. See https://github.com/InteractiveAdvertisingBureau/openrtb/blob/main/extensions/community_extensions/dsa_transparency.md for details.
- `privacy.dsa.default.transparency[].domain` - inject this domain value for this account. See https://github.com/InteractiveAdvertisingBureau/openrtb/blob/main/extensions/community_extensions/dsa_transparency.md for details.
- `privacy.dsa.default.transparency[].dsaparams` - inject this dsaparams value for this account. See https://github.com/InteractiveAdvertisingBureau/openrtb/blob/main/extensions/community_extensions/dsa_transparency.md for details.
- `privacy.dsa.gdpr-only` - When true, DSA default injection only happens when in GDPR scope. Defaults to false, meaning all the time.
- `privacy.allowactivities` - configuration for Activity Infrastructure. For further details, see: https://docs.prebid.org/prebid-server/features/pbs-activitycontrols.html
- `privacy.modules` - configuration for Privacy Modules. Each privacy module have own configuration.
- `analytics.allow-client-details` - when true, this boolean setting allows responses to transmit the server-side analytics tags to support client-side analytics adapters. Defaults to false.
- `analytics.auction-events.<channel>` - defines which channels are supported by analytics for this account
- `analytics.modules.<module-name>.*` - space for `module-name` analytics module specific configuration, may be of any shape
- `metrics.verbosity-level` - defines verbosity level of metrics for this account, overrides `metrics.accounts` application settings configuration. 
- `cookie-sync.default-limit` - if the "limit" isn't specified in the `/cookie_sync` request, this is what to use
- `cookie-sync.max-limit` - if the "limit" is specified in the `/cookie_sync` request, it can't be greater than this
  value
- `cookie-sync.pri` - a list of prioritized bidder codes
- `cookie-sync.coop-sync.default` - if the "coopSync" value isn't specified in the `/cookie_sync` request, use this
- `hooks` - configuration for Prebid Server Modules. For further details, see: https://docs.prebid.org/prebid-server/pbs-modules/index.html#2-define-an-execution-plan
- `settings.geo-lookup` - enables geo lookup for account if true.

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
        price-floors:
          enabled: false
        targeting:
            includewinners: false
            includebidderkeys: false
            includeformat: false
            preferdeals: false
            alwaysincludedeals: false
            prefix: hb
        debug-allow: true
      metrics:
        verbosity-level: basic
      privacy:
        ccpa:
          enabled: true
          channel-enabled:
            video: true
            web: true
            app: true
            amp: true
        gdpr:
          enabled: true
          channel-enabled:
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
        coop-sync:
          default: true
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
    },
    "price-floors": {
      "enabled": false
    },
    "debug-allow": true
  },
  "metrics": {
      "verbosity-level": "basic"
  },
  "privacy": {
    "ccpa": {
      "enabled": true,
      "channel-enabled": {
          "web": true,
          "amp": false,
          "app": true,
          "video": false
      }
    },
    "gdpr": {
      "enabled": true,
      "channel-enabled": {
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
    "coop-sync": {
      "default": true
    }
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
    `verbosity_level` varchar(64) DEFAULT NULL,
    
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
                       'metrics', JSON_OBJECT(
                               'verbosity-level', verbosity_level
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
