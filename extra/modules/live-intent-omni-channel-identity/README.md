# Overview

This module enriches bid requests with user IDs that it adds to the user EIDs. 

The user IDs to be enriched are configured on LiveIntent's side. The set of user IDs accessible by a partner are determined by the auth token provided in the settings.

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
            "all-processed-bid-responses": {
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


