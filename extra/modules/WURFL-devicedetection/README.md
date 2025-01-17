## WURFL Device Enrichment Module 

### Overview

The **WURFL Device Enrichment Module** for Prebid Server enhances the OpenRTB 2.x payload 
with comprehensive device detection data powered by **ScientiaMobile**’s WURFL device detection framework. 
Thanks to WURFL's device database, the module provides accurate and comprehensive device-related information, 
enabling bidders to make better-informed targeting and optimization decisions.

### Key features

#### Device Field Enrichment:

The WURFL module populates missing or empty fields in ortb2.device with the following data:
 - **make**: Manufacturer of the device (e.g., "Apple", "Samsung").
 - **model**: Device model (e.g., "iPhone 14", "Galaxy S22").
 - **os**: Operating system (e.g., "iOS", "Android").
 - **osv**: Operating system version (e.g., "16.0", "12.0").
 - **h**: Screen height in pixels.
 - **w**: Screen width in pixels.
 - **ppi**: Screen pixels per inch (PPI).
 - **pixelratio**: Screen pixel density ratio.
 - **devicetype**: Device type (e.g., mobile, tablet, desktop).
 - **Note**: If these fields are already populated in the bid request, the module will not overwrite them.
#### Publisher-Specific Enrichment:

Device enrichment is selectively enabled for publishers based on their account ID. 
The module identifies publishers through the `getAccount()` method in the `AuctionContext` class.


### Build prerequisites

To build the WURFL module, you need to download the WURFL Onsite Java API (both JAR and POM files) 
from the ScientiaMobile private repository and install it in your local Maven repository. 
Access to the WURFL Onsite Java API repository requires a valid ScientiaMobile WURFL license. 
For more details, visit: [ScientiaMobile WURFL Onsite API for Java](https://www.scientiamobile.com/secondary-products/wurfl-onsite-api-for-java/).

Run the following command to install the WURFL API:

```bash
mvn install:install-file \
    -Dfile=<path-to-your-wurfl-api-jar-file> \
    -DgroupId=com.scientiamobile.wurfl \
    -DartifactId=wurfl \
    -Dversion=<wurfl.version> \
    -Dpackaging=jar \
    -DpomFile=<path-to-your-wurfl-api-pom-file>
```

### Activating the WURFL Module

The WURFL module is disabled by default. Building the Prebid Server Java with the default bundle option 
does not include the WURFL module in the server's JAR file.

To include the WURFL module in the Prebid Server Java bundle, follow these steps:

1. Uncomment the WURFL Java API dependency in `extra/modules/WURFL-devicedetection/pom.xml`.
2. Uncomment the WURFL module dependency in `extra/bundle/pom.xml`.
3. Uncomment the WURFL module name in the module list in `extra/modules/pom.xml`.

After making these changes, you can build the Prebid Server Java bundle with the WURFL module using the following command:

```bash
mvn clean package --file extra/pom.xml
```

### Configuring the WURFL Module

Below is a sample configuration for the WURFL module:

```yaml
hooks:
  wurfl-devicedetection:
    enabled: true
  host-execution-plan: >
    {
      "endpoints": {
        "/openrtb2/auction": {
          "stages": {
            "entrypoint": {
              "groups": [
                {
                  "timeout": 10,
                  "hook_sequence": [
                    {
                      "module_code": "wurfl-devicedetection",
                      "hook_impl_code": "wurfl-devicedetection-entrypoint-hook"
                    }
                  ]
                }
              ]
            },
            "raw_auction_request": {
              "groups": [
                {
                  "timeout": 10,
                  "hook_sequence": [
                    {
                      "module_code": "wurfl-devicedetection",
                      "hook_impl_code": "wurfl-devicedetection-raw-auction-request"
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
    wurfl-devicedetection:
      wurfl-file-dir-path: </path/to/wurfl_snapshot_dir>
      wurfl-snapshot-url: https://data.scientiamobile.com/<your_wurfl_snapshot_url>/wurfl.zip
      cache-size: 200000
      wurfl-run-updater: true
      allowed-publisher-ids: 1
      ext-caps: false
```

### Configuration Options

| Parameter                 | Requirement | Description                                                                                           |
|---------------------------|-------------|-------------------------------------------------------------------------------------------------------|
| **`wurfl-file-dir-path`** | Mandatory   | Path to the directory where the WURFL file is downloaded. Directory must exist and be writable.       |
| **`wurfl-snapshot-url`**  | Mandatory   | URL of the licensed WURFL snapshot file to be downloaded when Prebid Server Java starts.             |
| **`cache-size`**          | Optional    | Maximum number of devices stored in the WURFL cache. Defaults to the WURFL cache's standard size.    |
| **`ext-caps`**            | Optional    | If `true`, the module adds all licensed capabilities to the `device.ext` object.                     |
| **`wurfl-run-updater`**   | Optional    | Enables the WURFL updater. Defaults to no updates.                                                   |
| **`allowed-publisher-ids`** | Optional  | List of publisher IDs permitted to use the module. Defaults to all publishers.                       |


A valid WURFL license must include all the required capabilities for device enrichment.

### Launching Prebid Server Java with the WURFL Module

After configuring the module and successfully building the Prebid Server bundle, start the server with the following command:

```bash
java -jar target/prebid-server-bundle.jar --spring.config.additional-location=sample/configs/prebid-config-with-wurfl.yaml
```

This sample configuration contains the module hook basic configuration. All the other module configuration options
are located in the `WURFL-devicedetection.yaml` inside the module. 

When the server starts, it downloads the WURFL file from the `wurfl-file-url` and loads it into the module.

Sample request data for testing is available in the module's `sample` directory. Using the `auction` endpoint, 
you can observe WURFL-enriched device data in the response.

### Sample Response

Using the sample request data via `curl` when the module is configured with `ext_caps` set to `false` (or no value)

```bash
curl http://localhost:8080/openrtb2/auction --data @extra/modules/WURFL-devicedetection/sample/request_data.json
```

the device object in the response will include WURFL device detection data:

```json
"device": {
  "ua": "Mozilla/5.0 (Linux; Android 15; Pixel 9 Pro XL Build/AP3A.241005.015;) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 EdgA/124.0.2478.64",
  "devicetype": 1,
  "make": "Google",
  "model": "Pixel 9 Pro XL",
  "os": "Android",
  "osv": "15",
  "h": 2992,
  "w": 1344,
  "ppi": 481,
  "pxratio": 2.55,
  "js": 1,
  "ext": {
    "wurfl": {
      "wurfl_id": "google_pixel_9_pro_xl_ver1_suban150"
    }
  }
}
```

When `ext_caps` is set to `true`, the response will include all licensed capabilities:

```json
"device":{
  "ua":"Mozilla/5.0 (Linux; Android 15; Pixel 9 Pro XL Build/AP3A.241005.015; ) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 EdgA/124.0.2478.64",
  "devicetype":1,
  "make":"Google",
  "model":"Pixel 9 Pro XL",
  "os":"Android",
  "osv":"15",
  "h":2992,
  "w":1344,
  "ppi":481,
  "pxratio":2.55,
  "js":1,
  "ext":{
    "wurfl":{
      "wurfl_id":"google_pixel_9_pro_xl_ver1_suban150",
      "mobile_browser_version":"",
      "resolution_height":"2992",
      "resolution_width":"1344",
      "is_wireless_device":"true",
      "is_tablet":"false",
      "physical_form_factor":"phone_phablet",
      "ajax_support_javascript":"true",
      "preferred_markup":"html_web_4_0",
      "brand_name":"Google",
      "can_assign_phone_number":"true",
      "xhtml_support_level":"4",
      "ux_full_desktop":"false",
      "device_os":"Android",
      "physical_screen_width":"71",
      "is_connected_tv":"false",
      "is_smarttv":"false",
      "physical_screen_height":"158",
      "model_name":"Pixel 9 Pro XL",
      "is_ott":"false",
      "density_class":"2.55",
      "marketing_name":"",
      "device_os_version":"15.0",
      "mobile_browser":"Chrome Mobile",
      "pointing_method":"touchscreen",
      "is_app_webview":"false",
      "advertised_app_name":"Edge Browser",
      "is_smartphone":"true",
      "is_robot":"false",
      "advertised_device_os":"Android",
      "is_largescreen":"true",
      "is_android":"true",
      "is_xhtmlmp_preferred":"false",
      "device_name":"Google Pixel 9 Pro XL",
      "is_ios":"false",
      "is_touchscreen":"true",
      "is_wml_preferred":"false",
      "is_app":"false",
      "is_mobile":"true",
      "is_phone":"true",
      "is_full_desktop":"false",
      "is_generic":"false",
      "advertised_browser":"Edge",
      "complete_device_name":"Google Pixel 9 Pro XL",
      "advertised_browser_version":"124.0.2478.64",
      "is_html_preferred":"true",
      "is_windows_phone":"false",
      "pixel_density":"481",
      "form_factor":"Smartphone",
      "advertised_device_os_version":"15"
    }
  }
}
```

## Maintainer

prebid@scientiamobile.com
