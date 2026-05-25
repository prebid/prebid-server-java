## Overview
Optable module operates using a DCN backend API. Please contact your account manager to get started.

The optable-targeting module enriches an incoming OpenRTB request by adding to the `user.eids` and `user.data`
objects. Under the hood the module extracts PPIDs (publisher provided IDs) from the incoming request's `user.ext.eids`,
and also if present sha256-hashed email, sha256-hashed phone, zip or Optable Visitor ID provided correspondingly in the
`user.ext.optable.email`, `.phone`, `.zip`, `.vid` fields (a full list of IDs is given in a table below). These IDs are
sent as input to the Targeting API. The received response data is used to enrich the OpenRTB request and response.
Targeting API endpoint is configurable per publisher.

## Setup

### Execution Plan

This module runs at three stages:

* Raw Auction Request: initiates a non-blocking Optable API call early in the auction lifecycle.
* Bidder Request: awaits the API response and enriches individual bidder requests with `user.eids` and `user.data`.
* Auction Response: injects ad server targeting.

We recommend defining the execution plan in the account config so the module is only invoked for specific accounts. See
below for an example.

### Global Config

There is no host-company level config for this module.

### Account-Level Config

To start using the module in PBS-Java you have to enable it and add the hooks into the execution plan in your config
file. Here's the recommended configuration:

```yaml
hooks:
  optable-targeting:
    enabled: true
  host-execution-plan: >
    {
      "endpoints": {
        "/openrtb2/auction": {
          "stages": {
            "raw-auction-request": {
              "groups": [
                {
                  "timeout": 50,
                  "hook-sequence": [
                    {
                      "module-code": "optable-targeting",
                      "hook-impl-code": "optable-targeting-raw-auction-request-hook"
                    }
                  ]
                }
              ]
            },
            "bidder-request": {
              "groups": [
                {
                  "timeout": 50,
                  "hook-sequence": [
                    {
                      "module-code": "optable-targeting",
                      "hook-impl-code": "optable-targeting-bidder-request-hook"
                    }
                  ]
                }
              ]
            },
            "auction-response": {
              "groups": [
                {
                  "timeout": 10,
                  "hook-sequence": [
                    {
                      "module-code": "optable-targeting",
                      "hook-impl-code": "optable-targeting-auction-response-hook"
                    }
                  ]
                }
              ]
            }
          }
        }
      }
    }
```

Sample module enablement configuration in JSON and YAML formats:

```json
{
  "modules":
  {
    "optable-targeting":
    {
      "api-endpoint": "endpoint",
      "api-key": "key",
      "timeout": 50,
      "enrichment-percentage": 100,
      "bidder-enrichment-percentages": {
        "appnexus": 75,
        "rubicon": 75,
        "pubmatic": 100,
        "criteo": 0
      },
      "enrich-web": true,
      "enrich-app": true,
      "ppid-mapping": {
        "pubcid.org": "c"
      },
      "adserver-targeting": false
    }
  }
}
```

```yaml
  modules:
    optable-targeting:
      api-endpoint: endpoint
      api-key: key
      timeout: 50
      enrichment-percentage: 100
      bidder-enrichment-percentages:
        appnexus: 75
        rubicon: 75
        pubmatic: 100
        criteo: 0
      enrich-web: true
      enrich-app: true
      ppid-mapping: {
        "pubcid.org": "c"
      }
      adserver-targeting: false
```

### Migrating from legacy configuration

Previous versions of the module used a `processed-auction-request` hook (alongside the `auction-response` hook) that both
made the API call and enriched the request synchronously in one step, blocking the auction pipeline. The new
configuration replaces it with two hooks: `raw-auction-request` (initiates the API call early) and `bidder-request`
(awaits the result and enriches per-bidder), while the `auction-response` hook remains unchanged. If your execution plan contains the following fragment, it should
be replaced with the `raw-auction-request` and `bidder-request` hooks shown above:

```json
"processed-auction-request": {
  "groups": [
    {
      "timeout": 600,
      "hook-sequence": [
        {
          "module-code": "optable-targeting",
          "hook-impl-code": "optable-targeting-processed-auction-request-hook"
        }
      ]
    }
  ]
}
```

The `processed-auction-request` hook is still supported for backwards compatibility. It detects whether the new hooks
(`raw-auction-request` and `bidder-request`) are present in the execution plan. If both are active, it passes through
immediately without blocking the pipeline. If the new hooks are absent, it falls back to the legacy synchronous
behavior. This means the legacy fragment can be kept during migration without negating the latency benefit of the new
configuration.

### Timeout considerations

The timeout value specified in the execution plan for the `bidder-request` hook should allow enough time for the Optable
Targeting API call to complete. The API call is initiated earlier in the `raw-auction-request` stage and runs in parallel
with other auction processing, so the effective wait time at the `bidder-request` stage is typically much shorter than
the full API roundtrip.

**Note:** Do not confuse hook timeout value with the module timeout parameter which is optional. The hook timeout value
would depend on the cloud/region where the PBS instance is hosted and the latency to reach the Optable's servers. This
will need to be verified experimentally upon deployment.

The timeout value for the `auction-response` can be set to 10 ms - usually it will be sub-millisecond time as there are
no HTTP calls made in this hook - Optable-specific keywords are cached on earlier stages and retrieved from the module
invocation context later.

## Module Configuration Parameters for PBS-Java

The parameter names are specified with full path using dot-notation. F.e. `section-name` .`sub-section` .`param-name`
would result in this nesting in the JSON configuration:

```json
{
  "section-name": {
    "sub-section": {
      "param-name": "param-value"
    }
  }
}
```


| Param Name                     | Required | Type    | Default value | Description                                                                                                                                                                                                                                                                                                                                                                                                                |
|:-------------------------------|:---------|:--------|:--------------|:---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| api-endpoint                   | yes      | string  | none          | Optable Targeting Edge API endpoint URL, required                                                                                                                                                                                                                                                                                                                                                                          |
| api-key                        | no       | string  | none          | If the API is protected with a key - this param needs to be specified to be sent in the auth header                                                                                                                                                                                                                                                                                                                        |
| ppid-mapping                   | no       | map     | none          | This specifies PPID source (`user.ext.eids[].source`) to a custom identifier prefix mapping, f.e. `{"example.com" : "c"}`. See the section on ID Mapping below for more detail.                                                                                                                                                                                                                                            |
| adserver-targeting             | no       | boolean | false         | If set to true - will add the Optable-specific adserver targeting keywords into the PBS response for every `seatbid[].bid[].ext.prebid.targeting`                                                                                                                                                                                                                                                                          |
| timeout                        | no       | integer | none          | A soft timeout (in ms) sent as a hint to the Targeting API endpoint to limit the request times to Optable's external tokenizer services                                                                                                                                                                                                                                                                                    |
| id-prefix-order                | no       | string  | none          | An optional string of comma separated id prefixes that prioritizes and specifies the order in which ids are provided to Targeting API in a query string. F.e. "c,c1,id5" will guarantee that Targeting API will see id=c:...,c1:...,id5:... if these ids are provided. id-prefixes not mentioned in this list will be added in arbitrary order after the priority prefix ids. This affects Targeting API processing logic  |
| enrichment-percentage          | no       | integer | 100           | Default percentage (0-100) of bid requests per bidder that will receive enrichment data. Set to 100 to enrich all requests, 0 to disable enrichment by default.                                                                                                                                                                                                                                                            |
| bidder-enrichment-percentages  | no       | map     | none          | Per-bidder overrides for `enrichment-percentage`. Keys are bidder names, values are percentages (0-100). F.e. `{"appnexus": 75, "criteo": 0}` enriches 75% of appnexus requests and none for criteo.                                                                                                                                                                                                                       |
| enrich-web                     | no       | boolean | true          | Whether to enrich web traffic (requests with a `site` object).                                                                                                                                                                                                                                                                                                                                                             |
| enrich-app                     | no       | boolean | true          | Whether to enrich app traffic (requests with an `app` object).                                                                                                                                                                                                                                                                                                                                                             |

## ID Mapping

Internally the module sends requests to Optable Targeting API. The output of Targeting API is used to enrich the request
and response. The below table describes the parameters that the module automatically fetches from OpenRTB request and
then sends to the Targeting API. The module will use a prefix as specified in the table to prepend the corresponding ID
value when sending it to the Targeting API in the form `id=prefix:value`.

See [Optable documentation](https://docs.optable.co/optable-documentation/dmp/reference/identifier-types#type-prefixes)
on identifier types. Targeting API accepts multiple id parameters - and their order may affect the results, thus
`id-prefix-order` specifies the order of the ids.


| Identifier Type                                                                | OpenRTB field                                                         | ID Type Prefix                           |
|--------------------------------------------------------------------------------|-----------------------------------------------------------------------|------------------------------------------|
| Email Address                                                                  | `user.ext.optable.email`                                              | `e:`                                     |
| Phone Number                                                                   | `user.ext.optable.phone`                                              | `p:`                                     |
| Postal Code                                                                    | `user.ext.optable.zip`                                                | `z:`                                     |
| IPv4 Address                                                                   | `device.ip`                                                           | ~~i4:~~ Sent as `X-Forwarded-For` header |
| IPv6 Address                                                                   | `device.ipv6`                                                         | ~~i6:~~ Sent as `X-Forwarded-For` header |
| Apple IDFA                                                                     | `device.ifa if lcase(device.os) contains 'ios' and device.lmt!=1`     | `a:`                                     |
| Google GAID                                                                    | `device.ifa if lcase(device.os) contains 'android' and device.lmt!=1` | `g:`                                     |
| Roku RIDA                                                                      | `device.ifa if lcase(device.os) contains 'roku' and device.lmt!=1`    | `r:`                                     |
| Samsung TV TIFA                                                                | `device.ifa if lcase(device.os) contains 'tizen' and device.lmt!=1`   | `s:`                                     |
| Amazon Fire AFAI                                                               | `device.ifa if lcase(device.os) contains 'fire' and device.lmt!=1`    | `f:`                                     |
| [NetID](https://docs.prebid.org/dev-docs/modules/userid-submodules/netid.html) | `user.ext.eids[].uids[0] when user.ext.eids[].source="netid.de"`      | `n:`                                     |
| [ID5](https://docs.prebid.org/dev-docs/modules/userid-submodules/id5.html)     | `user.ext.eids[].uids[0] when user.ext.eids[].source="id5-sync.com"`  | `id5:`                                   |
| [Utiq](https://docs.prebid.org/dev-docs/modules/userid-submodules/utiq.html)   | `user.ext.eids[].uids[0] when user.ext.eids[].source="utiq.com"`      | `utiq:`                                  |
| Optable VID                                                                    | `user.ext.optable.vid`                                                | `v:`                                     |

### Optable input erasure

**Note**: `user.ext.optable.email`, `.phone`, `.zip`, `.vid` fields will be removed by the module from the original
OpenRTB request before being sent to bidders.

### Publisher Provided IDs (PPID) Mapping

Custom user IDs are sent in the OpenRTB request in the
[`user.ext.eids[]`](https://github.com/InteractiveAdvertisingBureau/openrtb2.x/blob/main/2.6.md#3227---object-eid-).
The `ppid-mapping` allows to specify the mapping of a source to one of the custom identifier type prefixes `c`-`c19` -
see [documentation](https://docs.optable.co/optable-documentation/dmp/reference/identifier-types#type-prefixes), f.e.:

```yaml
ppid-mapping: {"example.com": "c2", "test.com": "c3"}
```

It is also possible to override any of the automatically retrieved `user.ext.eids[]` mentioned in the table above (s.a.
id5, utiq) so they are mapped to a different prefix. f.e. `id5-sync.com` can be mapped to a prefix other than `id5:`,
like:

```yaml
ppid-mapping: {"id5-sync.com": "c1"}
```

This will lead to id5 ID supplied as `id=c1:...` to the Targeting API.

## Analytics Tags

The following 2 analytics tags are written by the module:

* `optable-enrich-request`
* `optable-enrich-response`

The `status` is either `success` or `failure`.  Where it is `failure` a `results[0].value.reason` is provided.  
For the `optable-enrich-request` activity the `execution-time` value is logged.
Example:

```json
{
  "analytics": {
    "tags": [
      {
        "stage": "auction-response",
        "module": "optable-targeting",
        "analyticstags": {
          "activities": [
            {
              "name": "optable-enrich-request",
              "status": "success",
              "results": [
                {
                  "values": {
                    "execution-time": 33
                  }
                }
              ]
            },
            {
              "name": "optable-enrich-response",
              "status": "success",
              "results": [
                {
                  "values": {
                    "reason": "none"
                  }
                }
              ]
            }
          ]
        }
      }
    ]
  }
}
```

If `adserver-targeting` was set to `false` in the config `optable-enrich-response` analytics tag is not written.

## Running the demo (PBS-Java)

1. Build the server bundle JAR as described in [Build Project](https://github.com/prebid/prebid-server-java/blob/master/docs/build.md#build-project), e.g.

```bash
mvn clean package --file extra/pom.xml
```

2. In the `sample/configs/prebid-config-optable.yaml` file specify the `api-endpoint` URL of your DCN, f.e.:

```yaml
api-endpoint: https://example.com/v2/targeting
```

3. Start server bundle JAR as described in [Running project](https://github.com/prebid/prebid-server-java/blob/master/docs/run.md#running-project), e.g.

```bash
java -jar target/prebid-server-bundle.jar --spring.config.additional-location=sample/configs/prebid-config-with-optable.yaml
```

4. Run sample request against the server as described in [the sample directory](https://github.com/prebid/prebid-server-java/tree/master/sample), e.g.

```bash
curl http://localhost:8080/openrtb2/auction --data @extra/modules/optable-targeting/sample-requests/data.json
```

5. Observe the `user.eids` and `user.data` objects enriched.

## Maintainer contacts

Any suggestions or questions can be directed to [prebid@optable.co](mailto:prebid@optable.co).

Alternatively please open a new [issue](https://github.com/prebid/prebid-server-java/issues/new) or [pull request](https://github.com/prebid/prebid-server-java/pulls) in this repository.
