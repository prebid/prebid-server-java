# Overview

This module filters out bids from all the bid responses by the provided filtering logic for any given auction.
The filtering logic can be provided by enabling the hook.

## Configuration

To start using current module you have to enable module and add ``pb-richmedia-filter-all-processed-bid-responses-hook`` into hooks execution plan inside your yaml file:
```yaml
hooks:
  pb-richmedia-filter:
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
                      "module-code": "pb-richmedia-filter",
                      "hook-impl-code": "pb-richmedia-filter-all-processed-bid-responses-hook"
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

- `filter-mraid` - `true` enables the following logic: filter out any bid response that contains the provided `mraid-script-pattern` in the `adm` field
- `mraid-script-pattern` - a raw string with the MRAID script to be searched as it is

```yaml
hooks:
  modules:
    pb-richmedia-filter:
      filter-mraid: true
      mraid-script-pattern: >
        <script src="mraid.js"></script>
```

## Maintainer contacts

Any suggestions or questions can be directed by opening a new [issue](https://github.com/prebid/prebid-server-java/issues/new)
or [pull request](https://github.com/prebid/prebid-server-java/pulls) in this repository.
