## Overview

The IntentIQ Identity module enriches an incoming OpenRTB request by adding resolved IDs to
`user.eids`. At the `processed-auction-request` stage it calls the IntentIQ Bid Enhancement S2S API
(`ProfilesEngineServlet`) and merges the eids from the response into `user.eids` before the request
is sent to bidders. Please contact your IntentIQ account manager to get a partner token.

See the [S2S integration docs](https://s2s.documents.intentiq.com/) for the full API contract.

## Setup

### Execution Plan

This module runs at `processed-auction-request` (enrich `user.eids`) and, optionally, at `entrypoint` and
`raw-auction-request` (take the partner id out of the request, see
[Partner id in the bid request](#partner-id-in-the-bid-request)) and `auction-response` (report the winning
bid of each impression to `reports-endpoint`). Enable the module and add the hooks to the execution plan:

```yaml
hooks:
  intentiq-identity:
    enabled: true
  host-execution-plan: >
    {
      "endpoints": {
        "/openrtb2/auction": {
          "stages": {
            "entrypoint": {
              "groups": [
                { "timeout": 20, "hook-sequence": [
                  { "module-code": "intentiq-identity",
                    "hook-impl-code": "intentiq-identity-entrypoint-hook" } ] } ]
            },
            "raw-auction-request": {
              "groups": [
                { "timeout": 20, "hook-sequence": [
                  { "module-code": "intentiq-identity",
                    "hook-impl-code": "intentiq-identity-raw-auction-request-hook" } ] } ]
            },
            "processed-auction-request": {
              "groups": [
                { "timeout": 200, "hook-sequence": [
                  { "module-code": "intentiq-identity",
                    "hook-impl-code": "intentiq-identity-processed-auction-request-hook" } ] } ]
            },
            "auction-response": {
              "groups": [
                { "timeout": 50, "hook-sequence": [
                  { "module-code": "intentiq-identity",
                    "hook-impl-code": "intentiq-identity-auction-response-hook" } ] } ]
            }
          }
        }
      }
    }
  modules:
    intentiq-identity:
      api-endpoint: https://be-api-s2s.intentiq.com/profiles_engine/ProfilesEngineServlet
      reports-endpoint: https://reports-s2s.intentiq.com/profiles_engine/ProfilesEngineServlet
      partner-id: "1234567890"
      timeout: 1000
      reports-timeout: 1000
      cache-max-size: 100000
      cache:
        enabled: true
        ttlseconds: 43200
        max-keys: 10
        ttl-ceiling-first-party-seconds: 86400
        ttl-ceiling-third-party-seconds: 43200
        ttl-ceiling-device-seconds: 3600
        negative-ttl-seconds: 120
        in-progress-ttl-seconds: 30
        l2-timeout-ms: 30
      redis:
        host: localhost
        port: 6379
        max-pool-size: 32
        max-pool-waiting: 1024
```

Use the region-specific `api-endpoint`: US `be-api-s2s.intentiq.com`, EU `be-api-s2s-gdpr.intentiq.com`,
APAC `be-api-s2s-apac.intentiq.com`, and the matching `reports-endpoint`: US `reports-s2s.intentiq.com`,
EU `reports-s2s-gdpr.intentiq.com`. When `api-endpoint` is empty the hook is a no-op.

The `processed-auction-request` group timeout is how long the auction waits for IntentIQ. `timeout` is
how long the call itself may run. Keep `timeout` above the group timeout: a call that outlives the group
timeout keeps running in the background, and with caching on its result fills the cache, so the next
request of that user is enriched from the cache. With `timeout` at or below the group timeout, a slow call
is dropped and the auction proceeds without enrichment.

### Account-level config

Host-level config (above) provides defaults. Account-specific values can be set under the account's
`hooks.modules.intentiq-identity` config and are merged over the host defaults per request — so
`partner-id`, `timeout`, `reports-timeout` and `cache.enabled` can be set per account. The cache itself
(and every other `cache.*` setting) is built once from the host config: an account can switch caching off
with `cache.enabled: false`, but caching on an account needs it enabled on the host.

### Partner id in the bid request

A host that serves several IntentIQ partners can send the partner id in each bid request, in
`ext.prebid.intentiq.partnerId` or `ext.intentiq.partnerId`:

```json
{ "ext": { "prebid": { "intentiq": { "partnerId": "1234567890" } } } }
```

With Prebid.js, set it in the S2S config. `extPrebid` goes only into the request sent to Prebid Server, so no
client-side bidder sees it:

```javascript
pbjs.setConfig({ s2sConfig: [{ /* ... */ extPrebid: { intentiq: { partnerId: '1234567890' } } }] });
```

The value is a string or an integer. When both fields are set, `ext.prebid.intentiq` wins. It is used only when it is listed in `allowed-partner-ids`, so a
caller cannot act as a partner the host did not list:

```yaml
hooks:
  modules:
    intentiq-identity:
      partner-id: "1234567890"          # used when the request has no partner id, or one that is not listed
      allowed-partner-ids:
        - "1234567890"
        - "2345678901"
```

- A listed id is used for the resolution call, the cache scope, the impression report and the metrics.
- An id that is not listed is ignored: `partner-id` is used instead, and `partner.not_allowed` is counted.
  With no `partner-id` either, the auction runs without the module.
- With `allowed-partner-ids` empty (the default) the request field is ignored.
- `ext.prebid.intentiq` is read only by the `entrypoint` hook, on the raw body: PBS drops `ext.prebid` fields
  it does not know while it parses the request. The hook also removes it from the body.
- The `raw-auction-request` hook removes `ext.intentiq` at the first stage, before anything else reads the
  request, so no bidder, analytics adapter or debug output sees it. Neither hook does I/O, so neither can
  time out. Without the `raw-auction-request` hook the enrich hook removes `ext.intentiq`, but only when it
  finishes within its timeout: PBS drops every change of a hook that timed out.

## Module Configuration Parameters

| Param Name        | Level   | Required | Type    | Default  | Description                                                          |
|:------------------|:--------|:---------|:--------|:---------|:---------------------------------------------------------------------|
| api-endpoint      | host    | yes      | string  | none     | Bid Enhancement `ProfilesEngineServlet` URL (region-specific)        |
| reports-endpoint  | host    | no       | string  | none     | Impression-reporting `ProfilesEngineServlet` URL; blank disables it  |
| partner-id        | account | yes      | string  | none     | Partner token from IntentIQ, sent as the `dpi` query parameter       |
| allowed-partner-ids | account | no     | list    | empty    | Partner ids a request may pick with `ext.prebid.intentiq.partnerId` (see above) |
| timeout           | account | no       | long    | 1000     | HTTP timeout (ms) for the identity-resolution call                   |
| reports-timeout   | account | no       | long    | 1000     | HTTP timeout (ms) for the fire-and-forget impression report          |
| cache.enabled     | both    | no       | boolean | false    | Use the Caffeine + Redis cache (must be on at host level, with `redis.*`); an account can turn it off |
| cache.ttlseconds  | host    | no       | int     | 43200    | Fallback TTL (seconds) when the response omits `cttl` (12h)          |
| cache.max-keys    | host    | no       | int     | 10       | Max alias keys derived per request (caps eid-stuffed requests)       |
| cache.ttl-ceiling-first-party-seconds | host    | no | int | 86400 | Upper bound on TTL for first-party id keys (pubcid, MAID, other eids) |
| cache.ttl-ceiling-third-party-seconds | host    | no | int | 43200 | Upper bound on TTL for third-party id keys (`intentiq.com`)          |
| cache.ttl-ceiling-device-seconds       | host    | no | int | 3600  | Upper bound on TTL for the probabilistic device-composite key        |
| cache.negative-ttl-seconds             | host    | no | int | 120   | TTL for the negative (unresolvable id) sentinel                      |
| cache.in-progress-ttl-seconds          | host    | no | int | 30    | TTL for the IN_PROGRESS marker that dedups concurrent resolution calls; a failed call removes it at once |
| cache.l2-timeout-ms                    | host    | no | long | 30   | Max wait (ms) for an L2 read; a slower or hung store counts as a miss |
| cache-max-size    | host    | no       | long    | 100000   | Max entries in the local (Caffeine) layer                            |
| metrics-enabled   | host    | no       | boolean | true     | Record the module's `custom.*` metrics; set `false` to opt out       |
| redis.host        | host    | cond.    | string  | none     | Redis or Valkey host (required when caching)                         |
| redis.port        | host    | cond.    | int     | none     | Redis port (required when caching)                                   |
| redis.username    | host    | no       | string  | none     | Redis ACL user                                                       |
| redis.password    | host    | no       | string  | none     | Redis password (any characters; not part of a URL)                   |
| redis.ssl         | host    | no       | boolean | false    | Connect over TLS                                                     |
| redis.verify-hostname | host | no      | boolean | true     | With `ssl`: check that the server certificate matches `redis.host`   |
| redis.ca-cert-path | host   | no       | string  | none     | With `ssl`: PEM file of a private CA that signed the server certificate (applies to Redis only) |
| redis.cluster     | host    | no       | boolean | false    | Redis / Valkey cluster mode (e.g. ElastiCache Serverless or cluster mode enabled) |
| redis.max-pool-size    | host | no | int | 32   | Max pooled Redis connections                                         |
| redis.max-pool-waiting | host | no | int | 1024 | Max commands queued for a free connection before failing fast        |
| cache.provider    | host    | no       | string  | redis    | Shared (L2) store: `redis` (also Valkey) or `aerospike`              |
| aerospike.host    | host    | cond.    | string  | none     | Aerospike seed host (required with `cache.provider: aerospike`)      |
| aerospike.port    | host    | no       | int     | 3000     | Aerospike port                                                       |
| aerospike.namespace | host  | cond.    | string  | none     | Namespace (required); must allow TTLs, see below                     |
| aerospike.set     | host    | cond.    | string  | none     | Set (required)                                                       |
| aerospike.user    | host    | no       | string  | none     | User, when security is enabled (Enterprise Edition)                  |
| aerospike.password | host   | no       | string  | none     | Password                                                             |
| aerospike.timeout-ms | host | no       | int     | 100      | Total timeout of one read, write or delete                           |
| aerospike.connect-timeout-ms | host | no | int   | 1000     | Timeout of the initial cluster connection                            |
| aerospike.max-connections-per-node | host | no | int | 100 | Max async connections per node                                       |
| aerospike.event-loop-threads | host | no | int   | 2        | Threads of the Aerospike client's event loop                         |

The hook sends `at/mi/pt/dpn/srvrReq` constants plus `dpi` (= `partner-id`), and — when present on
the request — `ip`, `ipv6`, `uas`, `ref` (site domain / app name), `iiquid` (an existing
`intentiq.com` eid), and `pcid`+`idtype` from `device.ifa` (`idtype 4` for MAID/AAID, `idtype 8` for
CTV with the id upper-cased; skipped when `device.lmt = 1`). The response `data.eids` are merged into
`user.eids`, except a source the request already carries (OpenRTB allows one eid per source, so the
publisher's own eid is kept). On any failure, including a non-2xx answer, the hook takes no action and
the auction proceeds unchanged.

The call is skipped (no action) when the account has no `partner-id`, when the request is subject to
COPPA (`regs.coppa = 1`), or when GDPR applies (`regs.gdpr = 1`) and the request carries no consent
string (`user.consent`): IntentIQ rejects such a request. The consent string is sent in the `gdpr-consent` header.

### Activity controls

The module honors [activity controls](https://docs.prebid.org/prebid-server/features/pbs-activitycontrols.html)
as component type `general`, name `intentiq-identity`:

| Activity | Effect when not allowed |
|----------|-------------------------|
| `enrichUfpd` | no resolution call, `user.eids` unchanged |
| `reportAnalytics` | no impression report |
| `transmitUfpd` | user first party data and device ids (`device.ifa`) are not sent |
| `transmitEids` | `user.eids` are not sent (no `iiquid`) |
| `transmitPreciseGeo` | the IP is sent truncated |

For example, to keep the module from calling IntentIQ for one account:

```json
{"privacy": {"allowactivities": {"enrichUfpd": {"rules": [{
  "condition": {"componentType": ["general"], "componentName": ["intentiq-identity"]},
  "allow": false
}]}}}}
```

### Caching

When caching is enabled, resolved eids are cached in two layers: **Caffeine** (L1, in-process) backed
by an `IdentityStore` (L2, shared): **Redis** (or Valkey) by default, or **Aerospike** with
`cache.provider: aerospike`. L2 failures are non-fatal: the hook falls through to a live API call, and PBS
starts even when the store is down. A partner can use another backend by supplying their own
`IdentityStore` bean (the built-in store is `@ConditionalOnMissingBean`).

```yaml
      cache:
        enabled: true
        provider: aerospike
      aerospike:
        host: aerospike.internal
        namespace: cache
        set: iiq
```

Aerospike entries are written with a TTL, so the namespace must have `nsup-period` above 0 (or
`allow-ttl-without-nsup true`). Otherwise Aerospike rejects every write: the module still works, but only
from the local layer.

**Multi-key (alias) caching.** Every relevant first-party id on the request becomes a namespaced
alias key, ordered by priority: `iiq:<id>` (`intentiq.com`), `pubcid:<id>` (`pubcid.org` /
`sharedid.org`), `maid:<ifa>` (`device.ifa`; upper-cased for CTV, skipped when `device.lmt = 1`),
`<source>:<id>` for any other eid (e.g. `uidapi.com`). Only when the request carries none of these is a
probabilistic `dev:<ifa_ua_ip>` composite used (normalized UA; the ifa is left out under `lmt = 1`), so
users who share an IP are never given each other's identity. Every key is scoped by the partner id and a
digest of the request's privacy signals (`regs.gdpr`, consent, `us_privacy`, `gpp`): one partner's
resolution is never served to another, and a user whose consent or opt-out changed is resolved again.
Keys are de-duplicated and capped at `cache.max-keys`. On a lookup the
highest-priority key with a live entry wins, and that entry is **back-filled** under every other key
that missed — so the alias graph grows over time and a later request carrying any of those ids hits.
Differing resolutions are never merged (only the single winning entry propagates).

**TTL.** The response `cttl` (or `cache.ttlseconds` when omitted) always wins, but is capped per id
class by `cache.ttl-ceiling-{first-party,third-party,device}-seconds` — the cache holds the volatile
resolved eids, so ceilings are upper bounds, not the IntentIQ backend's long mapping TTLs.

**Negative caching.** When the API resolves no eids for a request, a negative sentinel is written
under all candidate keys so unresolvable ids do not re-hit the S2S API on every request; a negative
cache hit makes the hook take no action and skip the call. The suppression window honors the response
`cttl` when present (the backend signals how long to suppress for empty/invalid traffic, capped at the
first-party ceiling), falling back to `cache.negative-ttl-seconds` when `cttl` is absent.

**In-progress dedup.** On a full miss, before the live call an `IN_PROGRESS` marker
(`cache.in-progress-ttl-seconds`) is written under all candidate keys. A concurrent request for the
same id on the same PBS instance waits for the in-flight call and uses its result (eids and A/B
attribution); one on another instance reads the marker from Redis and proceeds without enrichment. The
in-flight call populates the cache for subsequent requests. The marker is overwritten by the resolved
(or negative) entry when the call completes, removed when the call fails (so the next request
retries), or expires if the call never completes. A resolved entry is always
preferred over the marker on read.

### Impression reporting

When `reports-endpoint` is set and the `auction-response` hook is in the execution plan, the module
reports the winning bid of each impression (the highest-priced bid per `impid` across all seats) to
the IntentIQ impression API — a fire-and-forget GET to
`<reports-endpoint>?at=45&rtype=1&dpi=<partner-id>&rdata=<UTF-8 URL-encoded JSON>`. The `rdata`
carries `bidderCode` (seat), `cpm`, `currency`, `placementId` (imp id), `biddingPlatformId=4`,
`vrref` (site domain / app), `ip`, `ua`, `prebidAuctionId`, and `abTestUuid`. The `abTestUuid` is the
one returned by the resolution response — the resolution hook stashes it in the module context and
this hook reads it. On a cache hit it is the `abTestUuid` (and `terminationCause`) of the response
that filled the cache entry. With
`reports-endpoint` blank the hook is a no-op. Nothing is reported when the account has no `partner-id`,
when the request is subject to COPPA, or when GDPR applies without a consent string (the report carries the user's IP and UA). The bid response is never modified.

### Debug trace

Send a request with `ext.prebid.trace: "verbose"` to see what the module did, per hook, in
`ext.prebid.modules.trace` of the response (`debugmessages`):

```
[enrich] 1 eid(s): intentiq.com; source: cache l1 hit (device key); tc=0, abTestUuid=ab-1; took 1 ms
[enrich] skipped: regs.coppa=1; no API call, user.eids unchanged
[report] sent 1 winning bid(s) to reports-endpoint (abTestUuid=ab-1)
```

The source is `IntentIQ API`, a cache hit, negative entry or in-progress marker (with layer and key type),
or the same user's call in flight on this instance. A hook that did not finish in time shows
`status: timeout` and no message.

### Metrics

The hook framework already emits per-module `call` / `success.*` / `failure` / `timeout` /
`execution-error` / `duration` for free under
`modules.module.intentiq-identity.stage.<stage>.hook.<hook>.…` (`<stage>` is `procauction` for the
request hook, `auctionresponse` for the response hook). The granularity is driven by what each hook
returns (`update` when enriched, `noAction` otherwise, `failed` on error).

In addition the module emits the custom counters below (implemented in `IntentiqIdentityMetrics`).
Recording is **on by default**; set `metrics-enabled: false` (host-level) to disable it entirely.
**Per-partner** metrics are suffixed with `_<dpi>` (the `partner-id`, never an internal backend id),
following the per-partner Graphite naming convention so the same per-partner Grafana templating applies; the
suffix is omitted when no partner id is configured. The `cache.*` outcome counters are additionally
broken down **by layer** (`l1` = Caffeine in-process, `l2` = Redis) and by `<keytype>` — `third_party`
(`intentiq.com`), `first_party` (pubcid / MAID / other eids), or `device` (the probabilistic UA+IP
composite); on a HIT/NEGATIVE/IN_PROGRESS the keytype is the key that matched, on a full miss the
request's highest-priority candidate.

```
modules.module.intentiq-identity.custom.cache.l1.hit.<keytype>_<dpi>          # positive entry served from L1 (Caffeine)
modules.module.intentiq-identity.custom.cache.l2.hit.<keytype>_<dpi>          # positive entry served from L2 (Redis)
modules.module.intentiq-identity.custom.cache.l1.negative.hit.<keytype>_<dpi> # negative sentinel from L1; counted as miss, no API call
modules.module.intentiq-identity.custom.cache.l2.negative.hit.<keytype>_<dpi> # negative sentinel from L2; counted as miss, no API call
modules.module.intentiq-identity.custom.cache.l1.in_progress.<keytype>_<dpi>  # in-flight marker from L1; duplicate API call skipped
modules.module.intentiq-identity.custom.cache.l2.in_progress.<keytype>_<dpi>  # in-flight marker from L2; duplicate API call skipped
modules.module.intentiq-identity.custom.cache.in_flight.joined_<dpi>          # request waited for the same user's call in flight on this instance
modules.module.intentiq-identity.custom.cache.miss.<keytype>_<dpi>            # full miss (neither L1 nor L2) -> API called
modules.module.intentiq-identity.custom.api.success_<dpi>                  # resolution API responded and parsed OK
modules.module.intentiq-identity.custom.api.error_<dpi>                    # resolution API failed/timed out/non-2xx/unparseable
modules.module.intentiq-identity.custom.api.latency_<dpi>                  # resolution API call duration (timer; every call)
modules.module.intentiq-identity.custom.flow.latency_<dpi>                 # whole-flow latency: enrich hook -> bid release (timer; per auction)
modules.module.intentiq-identity.custom.enriched_<dpi>                     # eids added to user.eids (a match)
modules.module.intentiq-identity.custom.eids.none_<dpi>                    # resolution produced no eids (pairs with enriched for match rate)
modules.module.intentiq-identity.custom.skip.no_endpoint_<dpi>             # no api-endpoint configured; resolution skipped before any API call
modules.module.intentiq-identity.custom.skip.no_partner                    # no partner-id configured; resolution skipped
modules.module.intentiq-identity.custom.partner.not_allowed                # requested partner id not in allowed-partner-ids; partner-id used
modules.module.intentiq-identity.custom.skip.no_consent_<dpi>              # regs.gdpr=1 without a consent string; resolution skipped
modules.module.intentiq-identity.custom.skip.coppa_<dpi>                   # regs.coppa=1; resolution and reporting skipped
modules.module.intentiq-identity.custom.skip.activity_<dpi>                # enrichUfpd / reportAnalytics not allowed; resolution / report skipped
modules.module.intentiq-identity.custom.tc.<id>_<dpi>                      # one counter per enumerated termination-cause id
modules.module.intentiq-identity.custom.impression.reported_<dpi>          # winning bid reported to reports-endpoint (overall)
modules.module.intentiq-identity.custom.impression.error_<dpi>             # impression report call failed or answered non-2xx
```

Shared L1 (Caffeine) / L2 (Redis) health is process-wide, so these are emitted **globally — without
the `_<dpi>` suffix** (one series each):

```
modules.module.intentiq-identity.custom.l1.size            # current L1 entry count (gauge; vs cache-max-size)
modules.module.intentiq-identity.custom.l1.eviction        # cumulative L1 evictions (gauge)
modules.module.intentiq-identity.custom.l1.get.error       # L1 read threw (≈never; treated as miss)
modules.module.intentiq-identity.custom.l1.put.error       # L1 write threw (≈never)
modules.module.intentiq-identity.custom.l2.get.latency     # L2 GET duration (timer; every probe)
modules.module.intentiq-identity.custom.l2.put.latency     # L2 PUT duration (timer; every write)
modules.module.intentiq-identity.custom.l2.get.error       # L2 GET failed -> fell through to live API (fail-open)
modules.module.intentiq-identity.custom.l2.put.error       # L2 PUT failed (entry still in L1, not in shared store)
modules.module.intentiq-identity.custom.l2.size            # Redis DBSIZE (gauge; polled ~30s; INSTANCE-WIDE)
modules.module.intentiq-identity.custom.l2.eviction        # Redis INFO evicted_keys (gauge; polled ~30s; INSTANCE-WIDE)
```

JVM / system health (free memory, GC) is provided by **prebid-server core**, not this module — enable
`metrics.jmx.enabled: true` and it registers `jvm.memory.*` and `jvm.gc.*` into the same registry.

> **Prometheus / scrape gotcha:** if these are scraped via the Prometheus `/metrics` endpoint, set
> `metrics.metricType: counter` — **not** the default `flushingCounter`, which resets after each
> report (correct for Graphite/InfluxDB push, wrong for scrape) and would make the counters read as
> near-zero on every scrape. The server logs a warning when Prometheus is enabled with
> `flushingCounter`.

### Sending the metrics to IntentIQ

Optional. With a URL and token from IntentIQ, the module also posts its metrics to IntentIQ every minute, in
the Prometheus text format. Only `modules.module.intentiq-identity.*` metrics are sent, nothing else from the
server. It never touches the server's own reporters: a counter that resets on read (`flushingCounter`) is
skipped. A failed or slow push is dropped, never queued or retried, and the auction never waits for it.

```yaml
hooks:
  modules:
    intentiq-identity:
      metrics-push:
        url: <given by IntentIQ>
        token: ${IIQ_METRICS_TOKEN}   # keep the token in an environment variable
        instance: <optional, defaults to the host name>
        interval-seconds: 60
        timeout-ms: 5000
```

| Parameter        | Scope | Required | Type    | Default   | Description                              |
|------------------|-------|----------|---------|-----------|------------------------------------------|
| url              | host  | no       | string  | none      | Push URL; off unless url and token set   |
| token            | host  | no       | string  | none      | Bearer token                             |
| instance         | host  | no       | string  | host name | `instance` label of this server          |
| interval-seconds | host  | no       | integer | 60        | Push interval                            |
| timeout-ms       | host  | no       | long    | 5000      | Timeout of one push                      |

Its own health: `custom.push.success`, `custom.push.error`, `custom.push.skipped` (a push still running when
the next one was due).

## Running the demo

1. Build the bundle: `mvn clean package --file extra/pom.xml`
2. Set `api-endpoint` and `partner-id` in `sample/configs/prebid-config-with-intentiq.yaml`.
3. Run:
   `java -jar target/prebid-server-bundle.jar --spring.config.additional-location=sample/configs/prebid-config-with-intentiq.yaml`
4. POST a request to `/openrtb2/auction` and observe `user.eids` enriched in `ext.debug.resolvedrequest`.

## Maintainer contacts

Any suggestions or questions can be directed to the IntentIQ team. Alternatively please open a new
[issue](https://github.com/prebid/prebid-server-java/issues/new) or
[pull request](https://github.com/prebid/prebid-server-java/pulls) in this repository.
