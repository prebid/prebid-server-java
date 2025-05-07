# Overview

This module enriches bid requests with user EIDs.

The user EIDs to be enriched are configured per partner as part of the LiveIntent HIRO onboarding process. 

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
```
The partner-specific `auth-token` is provided by LiveIntent as part of the onboarding.

