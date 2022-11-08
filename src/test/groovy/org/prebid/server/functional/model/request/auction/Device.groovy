package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

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
}
