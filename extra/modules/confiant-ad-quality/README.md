# Overview

This module allows obtains all bid responses for any given auction, send the results to Confiant server and find possible security issues inside bid responses.

## Configuration

To start using current module you have to enable module and add ``confiant-ad-quality-bid-responses-scan-hook`` into hooks execution plan inside your yaml file:
```yaml
hooks:
    confiant-ad-quality:
        enabled: true
    host-execution-plan: >
        {
          "endpoints": {
            "/openrtb2/auction": {
              "stages": {
                "all-processed-bid-responses": {
                  "groups": [
                    {
                      "timeout": 5,
                      "hook-sequence": [
                        {
                          "module-code": "confiant-ad-quality",
                          "hook-impl-code": "confiant-ad-quality-bid-responses-scan-hook"
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
And configure

## List of module configuration options

- `api-key` - Confiant's API key.
- `redis-host` - Host value of the Confiant's Redis server.
- `redis-port` - Port value of the Confiant's Redis server.
- `redis-password` - User password value of the Confiant's Redis server.

```yaml
hooks:
  modules:
    confiant-ad-quality:
      api-key: "hgr876cerg7655"
      redis-host: "127.0.0.1"
      redis-port: 8000
      redis-password: "JhgYYttq76"
```

## Maintainer contacts

Any suggestions or questions can be directed to [support@confiant.com](support@confiant.com)
e-mail.

Or just open new [issue](https://github.com/prebid/prebid-server-java/issues/new)
or [pull request](https://github.com/prebid/prebid-server-java/pulls) in this repository.
