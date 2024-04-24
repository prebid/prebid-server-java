# Overview

51Degrees module enriches an incoming OpenRTB request with the device data using [51Degrees Device Detection](https://51degrees.com/documentation/4.4/_device_detection__overview.html).   51Degrees detects and sets the following fields of the device object: `make`, `model`, `os`, `osv`, `h`, `w`, `ppi`, `pixelratio` - interested bidders may use these fields as needed.  In addition the module sets `device.ext.fiftyonedegrees_deviceId` to a permanent device ID which may be used with a 51Degrees data file to look up over 250 properties on the backend.

## Setup

The 51Degrees module operates using a data file. You can get started with a free Lite data file that can be downloaded here: [https://github.com/51Degrees/device-detection-data/blob/main/51Degrees-LiteV4.1.hash](https://github.com/51Degrees/device-detection-data/blob/main/51Degrees-LiteV4.1.hash).  The Lite file is capable of detecting limited device information, so if you need in-depth device data, please contact 51Degrees to obtain a paid license: [https://51degrees.com/contact-us](https://51degrees.com/contact-us?ContactReason=Free%20Trial).

Put the data file in a file system location writable by the user that is running the Prebid Server module and specify that directory location in the configuration parameters. The location needs to be writable if you would like to enable [automatic data file updates](https://51degrees.com/documentation/_features__automatic_datafile_updates.html).

## Configuration

To start using current module you have to enable module and add `fiftyone-devicedetection-entrypoint-hook` and `fiftyone-devicedetection-raw-auction-request-hook` into hooks execution plan inside your yaml file:

```yaml
hooks:
  fiftyone-devicedetection:
    enabled: true
  host-execution-plan: >
    {
      "endpoints": {
        "/openrtb2/auction": {
          "stages": {
            "entrypoint": {
              "groups": [
                {
                  "timeout": 100,
                  "hook-sequence": [
                    {
                      "module-code": "fiftyone-devicedetection",
                      "hook-impl-code": "fiftyone-devicedetection-entrypoint-hook"
                    }
                  ]
                }
              ]
            },
            "raw-auction-request": {
              "groups": [
                {
                  "timeout": 100,
                  "hook-sequence": [
                    {
                      "module-code": "fiftyone-devicedetection",
                      "hook-impl-code": "fiftyone-devicedetection-raw-auction-request-hook"
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

- `account-filter`
  - `allow-list` - _(list of strings)_ -  A list of account IDs that are allowed to use this module. If empty, everyone is allowed. Full-string match is performed (whitespaces and capitalization matter). Defaults to empty.
- `data-file`
  - `path` - _(string, **REQUIRED**)_ -  The full path to the device detection data file. Sample file can be downloaded from [[data repo on GitHub](https://github.com/51Degrees/device-detection-data/blob/main/51Degrees-LiteV4.1.hash)].
  - `make-temp-copy` - _(boolean)_ - If true, the engine will create a temporary copy of the data file rather than using the data file directly. Defaults to false.
  - `update`
    - `auto` - _(boolean)_ - Enable/Disable auto update. Defaults to enabled. If enabled, the auto update system will automatically download and apply new data files for device detection.
    - `on-startup` - _(boolean)_ - Enable/Disable update on startup. Defaults to enabled. If enabled, the auto update system will be used to check for an update before the device detection engine is created. If an update is available, it will be downloaded and applied before the pipeline is built and returned for use so this may take some time.
    - `url` - _(string)_ - Configure the engine to use the specified URL when looking for an updated data file. Default is the 51Degrees update URL.
    - `license-key` - _(string)_ - Set the license key used when checking for new device detection data files. Defaults to null.
    - `watch-file-system` - _(boolean)_ - The DataUpdateService has the ability to watch a file on disk and refresh the engine as soon as that file is updated. This setting enables/disables that feature. Defaults to true.
    - `polling-interval` - _(int, seconds)_ - Set the time between checks for a new data file made by the DataUpdateService in seconds. Default = 30 minutes.
- `performance`
  - `profile` - _(string)_ - Set the performance profile for the device detection engine. Must be one of: LowMemory, MaxPerformance, HighPerformance, Balanced, BalancedTemp. Defaults to balanced.
  - `concurrency` - _(int)_ - Set the expected number of concurrent operations using the engine. This sets the concurrency of the internal caches to avoid excessive locking. Default: 10.
  - `difference` - _(int)_ - Set the maximum difference to allow when processing HTTP headers. The meaning of difference depends on the Device Detection API being used. The difference is the difference in hash value between the hash that was found, and the hash that is being searched for. By default this is 0.
  - `allow-unmatched` - _(boolean)_ - If set to false, a non-matching User-Agent will result in properties without set values.
  If set to true, a non-matching User-Agent will cause the 'default profiles' to be returned. This means that properties will always have values (i.e. no need to check .hasValue) but some may be inaccurate. By default, this is false.
  - `drift` - _(int)_ - Set the maximum drift to allow when matching hashes. If the drift is exceeded, the result is considered invalid and values will not be returned. By default this is 0.

```yaml
hooks:
  modules:
    fiftyone-devicedetection:
      account-filter:
        allow-list: [] # list of strings
      data-file:
        path: ~ # string, REQUIRED
        make-temp-copy: ~ # boolean
        update:
          auto: ~ # boolean
          on-startup: ~ # boolean
          url: ~ # string
          license-key: ~ # string
          watch-file-system: ~ # boolean
          polling-interval: ~ # int, seconds
      performance:
        profile: ~ # string, 1 of [LowMemory,MaxPerformance,HighPerformance,Balanced,BalancedTemp]
        concurrency: ~ # int
        difference: ~ # int
        allow-unmatched: ~ # boolean
        drift: ~ # int
```

Minimal sample (only required):

```yaml
  modules:
    fiftyone-devicedetection:
      data-file:
        path: "../device-detection-cxx/device-detection-data/51Degrees-LiteV4.1.hash" # REQUIRED, download the sample from https://github.com/51Degrees/device-detection-data/blob/main/51Degrees-LiteV4.1.hash
```

## Maintainer contacts

Any suggestions or questions can be directed to [engineering@51degrees.com](engineering@51degrees.com) e-mail.

Or just open new [issue](https://github.com/prebid/prebid-server-java/issues/new) or [pull request](https://github.com/prebid/prebid-server-java/pulls) in this repository.
