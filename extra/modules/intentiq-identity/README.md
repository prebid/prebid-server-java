## Overview

The IntentIQ Identity module enriches an incoming OpenRTB request by adding resolved IDs to
`user.eids`. At the `processed-auction-request` stage it calls the IntentIQ Bid Enhancement S2S API
(`ProfilesEngineServlet`) and merges the eids from the response into `user.eids` before the request
is sent to bidders. Please contact your IntentIQ account manager to get a partner token.

See the [S2S integration docs](https://s2s.documents.intentiq.com/) for the full API contract.

## Setup

### Execution Plan

This module runs at two stages: `processed-auction-request` (enrich `user.eids`) and, optionally,
`auction-response` (report winning bids as impressions to `reports-endpoint`). Enable the module and
add the hook(s) to the execution plan:

```yaml
hooks:
  intentiq-identity:
    enabled: true
  host-execution-plan: >
    {
      "endpoints": {
        "/openrtb2/auction": {
          "stages": {
            "processed-auction-request": {
              "groups": [
                { "timeout": 100, "hook-sequence": [
                  { "module-code": "intentiq-identity",
                    "hook-impl-code": "intentiq-identity-processed-auction-request-hook" } ] } ]
            },
            "auction-response": {
              "groups": [
                { "timeout": 100, "hook-sequence": [
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
      cache-max-size: 100000
      cache:
        enabled: true
        ttlseconds: 43200
        max-keys: 10
        ttl-ceiling-first-party-seconds: 86400
        ttl-ceiling-third-party-seconds: 43200
        ttl-ceiling-device-seconds: 3600
        negative-ttl-seconds: 120
        in-progress-ttl-seconds: 1800
      redis:
        host: localhost
        port: 6379
```

Use the region-specific `api-endpoint`: US `be-api-s2s.intentiq.com`, EU `be-api-s2s-gdpr.intentiq.com`,
APAC `be-api-s2s-apac.intentiq.com`. When `api-endpoint` is empty the hook is a no-op.

### Account-level config

Host-level config (above) provides defaults. Account-specific values can be set under the account's
`hooks.modules.intentiq-identity` config and are merged over the host defaults per request — so
`partner-id`, `timeout`, and `cache.*` can be tuned per account.

## Module Configuration Parameters

| Param Name        | Level   | Required | Type    | Default  | Description                                                          |
|:------------------|:--------|:---------|:--------|:---------|:---------------------------------------------------------------------|
| api-endpoint      | host    | yes      | string  | none     | Bid Enhancement `ProfilesEngineServlet` URL (region-specific)        |
| reports-endpoint  | host    | no       | string  | none     | Impression-reporting `ProfilesEngineServlet` URL; blank disables it  |
| partner-id        | account | yes      | string  | none     | Partner token from IntentIQ, sent as the `dpi` query parameter       |
| timeout           | account | no       | long    | 1000     | HTTP timeout (ms) for the identity-resolution call                   |
| cache.enabled     | account | no       | boolean | false    | Use the Caffeine + Redis cache (host must configure `redis.*`)       |
| cache.ttlseconds  | account | no       | int     | 43200    | Fallback TTL (seconds) when the response omits `cttl` (12h)          |
| cache.max-keys    | account | no       | int     | 10       | Max alias keys derived per request (caps eid-stuffed requests)       |
| cache.ttl-ceiling-first-party-seconds | account | no | int | 86400 | Upper bound on TTL for first-party id keys (pubcid, MAID, other eids) |
| cache.ttl-ceiling-third-party-seconds | account | no | int | 43200 | Upper bound on TTL for third-party id keys (`intentiq.com`)          |
| cache.ttl-ceiling-device-seconds       | account | no | int | 3600  | Upper bound on TTL for the probabilistic device-composite key        |
| cache.negative-ttl-seconds             | account | no | int | 120   | TTL for the negative (unresolvable id) sentinel                      |
| cache.in-progress-ttl-seconds          | account | no | int | 1800  | TTL for the IN_PROGRESS marker that dedups concurrent resolution calls (matches the backend's 30m window) |
| cache-max-size    | host    | no       | long    | 100000   | Max entries in the local (Caffeine) layer                            |
| metrics-enabled   | host    | no       | boolean | true     | Record the module's `custom.*` metrics; set `false` to opt out       |
| redis.host        | host    | cond.    | string  | none     | Redis host (required when caching)                                   |
| redis.port        | host    | cond.    | int     | none     | Redis port (required when caching)                                   |
| redis.password    | host    | no       | string  | none     | Redis password                                                       |

The hook sends `at/mi/pt/dpn/srvrReq` constants plus `dpi` (= `partner-id`), and — when present on
the request — `ip`, `ipv6`, `uas`, `ref` (site domain / app name), `iiquid` (an existing
`intentiq.com` eid), and `pcid`+`idtype` from `device.ifa` (`idtype 4` for MAID/AAID, `idtype 8` for
CTV with the id upper-cased; skipped when `device.lmt = 1`). The response `data.eids` are merged into
`user.eids`; on any failure the hook takes no action and the auction proceeds unchanged.

### Caching

When caching is enabled, resolved eids are cached in two layers: **Caffeine** (L1, in-process) backed
by an `IdentityStore` (L2, shared) — **Redis** by default. L2 failures are non-fatal: the hook falls
through to a live API call. A partner can use a non-Redis backend by supplying their own
`IdentityStore` bean (the default Redis store is `@ConditionalOnMissingBean`).

**Multi-key (alias) caching.** Every relevant first-party id on the request becomes a namespaced
alias key, ordered by priority: `iiq:<id>` (`intentiq.com`), `pubcid:<id>` (`pubcid.org` /
`sharedid.org`), `maid:<ifa>` (`device.ifa`; upper-cased for CTV, skipped when `device.lmt = 1`),
`<source>:<id>` for any other eid (e.g. `uidapi.com`), and a probabilistic `dev:<ifa_ua_ip>` composite
as last resort. Keys are de-duplicated and capped at `cache.max-keys`. On a lookup the
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
same id reads the marker and skips firing a duplicate call (it proceeds without enrichment); the
in-flight call populates the cache for subsequent requests. The marker is overwritten by the resolved
(or negative) entry when the call completes, or expires if it never does. A resolved entry is always
preferred over the marker on read.

### Impression reporting

When `reports-endpoint` is set and the `auction-response` hook is in the execution plan, the module
reports each winning `seatbid[].bid[]` to the IntentIQ impression API — a fire-and-forget GET to
`<reports-endpoint>?at=45&rtype=1&dpi=<partner-id>&rdata=<UTF-8 URL-encoded JSON>`. The `rdata`
carries `bidderCode` (seat), `cpm`, `currency`, `placementId` (imp id), `biddingPlatformId=4`,
`vrref` (site domain / app), `ip`, `ua`, `prebidAuctionId`, and `abTestUuid`. The `abTestUuid` is the
one returned by the resolution response — the resolution hook stashes it in the module context and
this hook reads it (omitted on a cache hit, since no fresh IIQ response was produced). With
`reports-endpoint` blank the hook is a no-op. The bid response is never modified.

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
modules.module.intentiq-identity.custom.cache.miss.<keytype>_<dpi>            # full miss (neither L1 nor L2) -> API called
modules.module.intentiq-identity.custom.api.success_<dpi>                  # resolution API responded and parsed OK
modules.module.intentiq-identity.custom.api.error_<dpi>                    # resolution API failed/timed out/unparseable
modules.module.intentiq-identity.custom.api.latency_<dpi>                  # resolution API call duration (timer; every call)
modules.module.intentiq-identity.custom.flow.latency_<dpi>                 # whole-flow latency: enrich hook -> bid release (timer; per auction)
modules.module.intentiq-identity.custom.enriched_<dpi>                     # eids added to user.eids (a match)
modules.module.intentiq-identity.custom.eids.none_<dpi>                    # resolution produced no eids (pairs with enriched for match rate)
modules.module.intentiq-identity.custom.skip.no_endpoint_<dpi>             # no api-endpoint configured; resolution skipped before any API call
modules.module.intentiq-identity.custom.tc.<id>_<dpi>                      # one counter per enumerated termination-cause id
modules.module.intentiq-identity.custom.impression.reported_<dpi>          # winning bid reported to reports-endpoint (overall)
modules.module.intentiq-identity.custom.impression.error_<dpi>             # impression report call failed
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
