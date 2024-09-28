# Overview

51Degrees module enriches an incoming OpenRTB request [51Degrees Device Data](https://51degrees.com/documentation/_device_detection__overview.html).

51Degrees module sets the following fields of the device object: `make`, `model`, `os`, `osv`, `h`, `w`, `ppi`, `pixelratio` - interested bidder adapters may use these fields as needed.  In addition the module sets `device.ext.fiftyonedegrees_deviceId` to a permanent device ID which can be rapidly looked up in on premise data exposing over 250 properties including the device age, chip set, codec support, and price, operating system and app/browser versions, age, and embedded features.

## Setup

The 51Degrees module operates using a data file. You can get started with a free Lite data file that can be downloaded here: [https://github.com/51Degrees/device-detection-data/blob/main/51Degrees-LiteV4.1.hash](https://github.com/51Degrees/device-detection-data/blob/main/51Degrees-LiteV4.1.hash).  The Lite file is capable of detecting limited device information, so if you need in-depth device data, please contact 51Degrees to obtain a license: [https://51degrees.com/contact-us](https://51degrees.com/contact-us?ContactReason=Free%20Trial).

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
  - `difference` - _(int)_ - Set the maximum difference to allow when processing HTTP headers. The meaning of difference depends on the Device Detection API being used. The difference is the difference in hash value between the hash that was found, and the hash that is being searched for. By default this is 0. For more information see [51Degrees documentation](https://51degrees.com/documentation/_device_detection__hash.html).
  - `allow-unmatched` - _(boolean)_ - If set to false, a non-matching User-Agent will result in properties without set values.
  If set to true, a non-matching User-Agent will cause the 'default profiles' to be returned. This means that properties will always have values (i.e. no need to check .hasValue) but some may be inaccurate. By default, this is false.
  - `drift` - _(int)_ - Set the maximum drift to allow when matching hashes. If the drift is exceeded, the result is considered invalid and values will not be returned. By default this is 0. For more information see [51Degrees documentation](https://51degrees.com/documentation/_device_detection__hash.html).

```yaml
hooks:
  modules:
    fiftyone-devicedetection:
      account-filter:
        allow-list: [] # list of strings, account ids for enabled publishers, or empty for all
      data-file:
        path: ~ # string, REQUIRED, download the sample from https://github.com/51Degrees/device-detection-data/blob/main/51Degrees-LiteV4.1.hash or Enterprise from https://51degrees.com/pricing
        make-temp-copy: ~ # boolean
        update:
          auto: ~ # boolean
          on-startup: ~ # boolean
          url: ~ # string
          license-key: ~ # string
          watch-file-system: ~ # boolean
          polling-interval: ~ # int, seconds
      performance:
        profile: ~ # string, one of [LowMemory,MaxPerformance,HighPerformance,Balanced,BalancedTemp]
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
        path: "51Degrees-LiteV4.1.hash" # string, REQUIRED, download the sample from https://github.com/51Degrees/device-detection-data/blob/main/51Degrees-LiteV4.1.hash or Enterprise from https://51degrees.com/pricing
```

## Running the demo

1. Build the server bundle JAR as described in [[Build Project](../../../docs/build.md#build-project)], e.g.

```bash
mvn clean package --file extra/pom.xml
```

2. Download `51Degrees-LiteV4.1.hash` from [[GitHub](https://github.com/51Degrees/device-detection-data/blob/main/51Degrees-LiteV4.1.hash)] and put it in the project root directory.

```bash
curl -o 51Degrees-LiteV4.1.hash -L https://github.com/51Degrees/device-detection-data/raw/main/51Degrees-LiteV4.1.hash
```

3. Start server bundle JAR as described in [[Running project](../../../docs/run.md#running-project)], e.g.

```bash
java -jar target/prebid-server-bundle.jar --spring.config.additional-location=sample/prebid-config-with-51d-dd.yaml
```

4. Run sample request against the server as described in [[requests/README](../../../sample/requests/README.txt)], e.g.

```bash
curl http://localhost:8080/openrtb2/auction --data @extra/modules/fiftyone-devicedetection/sample-requests/data.json
```

5. See the `device` object be enriched

```diff
                 "device": {
-                    "ua": "Mozilla/5.0 (Linux; Android 11; SM-G998W) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.198 Mobile Safari/537.36"
+                    "ua": "Mozilla/5.0 (Linux; Android 11; SM-G998W) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.198 Mobile Safari/537.36",
+                    "os": "Android",
+                    "osv": "11.0",
+                    "h": 3200,
+                    "w": 1440,
+                    "ext": {
+                        "fiftyonedegrees_deviceId": "110698-102757-105219-0"
+                    }
                 },
```

[[Enterprise](https://51degrees.com/pricing)] files can provide even more information:

```diff
                 "device": {
                     "ua": "Mozilla/5.0 (Linux; Android 11; SM-G998W) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.198 Mobile Safari/537.36",
+                    "devicetype": 1,
+                    "make": "Samsung",
+                    "model": "SM-G998W",
                     "os": "Android",
                     "osv": "11.0",
                     "h": 3200,
                     "w": 1440,
+                    "ppi": 516,
+                    "pxratio": 3.44,
                     "ext": {
-                        "fiftyonedegrees_deviceId": "110698-102757-105219-0"
+                        "fiftyonedegrees_deviceId": "110698-102757-105219-18092"
                     }
```

## Maintainer contacts

Any suggestions or questions can be directed to [support@51degrees.com](support@51degrees.com) e-mail.

Or just open new [issue](https://github.com/prebid/prebid-server-java/issues/new) or [pull request](https://github.com/prebid/prebid-server-java/pulls) in this repository.