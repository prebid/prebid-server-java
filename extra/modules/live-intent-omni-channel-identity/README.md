# Overview

This module enriches bid requests with user EIDs.

The user EIDs to be enriched are configured per partner as part of the LiveIntent HIRO onboarding process. As part of this onboarding process, partners will also be provided with the `identity-resolution-endpoint` URL as well as with the `auth-token`.

`treatment-rate` is a value between 0.0 and 1.0 (including 0.0 and 1.0) and defines the percentage of requests for which identity enrichment should be performed. This value can be freely picked. We recommend a value between 0.9 and 0.95

## Configuration

To start using the LiveIntent Omni Channel Identity module you have to enable it and add configuration:

```yaml
hooks:
  liveintent-omni-channel-identity:
    enabled: true
  host-execution-plan: >
    {
      "endpoints": {
        "/openrtb2/auction": {
          "stages": {
            "processed-auction-request": {
              "groups": [
                {
                  "timeout": 100,
                  "hook-sequence": [
                    {
                      "module-code": "liveintent-omni-channel-identity",
                      "hook-impl-code": "liveintent-omni-channel-identity-enrichment-hook"
                    }
                  ]
                }
              ]
            }
          }
        }
      }
    }
  modules:
    liveintent-omni-channel-identity:
      request-timeout-ms: 2000
      identity-resolution-endpoint: "https://liveintent.com/idx"
      auth-token: "secret-token"
      treatment-rate: 0.9
```

