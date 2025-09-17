package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString
<<<<<<< HEAD
import org.prebid.server.functional.util.PBSUtils
=======
>>>>>>> 04d9d4a13 (Initial commit)

@ToString(includeNames = true, ignoreNulls = true)
class Device {

    Geo geo
    Integer dnt
    Integer lmt
    String ua
    UserAgent sua
    String ip
    String ipv6
    Integer devicetype
    String make
    String model
    String os
    String osv
    String hwv
    Integer h
    Integer w
    Integer ppi
    BigDecimal pxratio
    Integer js
    Integer geofetch
    String flashver
    String language
    String langb
    String carrier
    String mccmnc
    Integer connectiontype
    String ifa
    String didsha1
    String didmd5
    String dpidsha1
    String dpidmd5
    String macsha1
    String macmd5
    DeviceExt ext
<<<<<<< HEAD

    static Device getDefault() {
        new Device().tap {
            didsha1 = PBSUtils.randomString
            didmd5 = PBSUtils.randomString
            dpidsha1 = PBSUtils.randomString
            ifa = PBSUtils.randomString
            macsha1 = PBSUtils.randomString
            macmd5 = PBSUtils.randomString
            dpidmd5 = PBSUtils.randomString
        }
    }
=======
>>>>>>> 04d9d4a13 (Initial commit)
}
