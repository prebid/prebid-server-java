package org.prebid.server.functional.model.request.auction

enum PublicCountryIp {

    USA_IP("209.232.44.21", "d646:2414:17b2:f371:9b62:f176:b4c0:51cd"),
    UKR_IP("193.238.111.14", "3080:f30f:e4bc:0f56:41be:6aab:9d0a:58e2"),
    CAN_IP("70.71.245.39", "f9b2:c742:1922:7d4b:7122:c7fc:8b75:98c8")

    final String v4
    final String v6

    PublicCountryIp(String v4, String ipV6) {
        this.v4 = v4
        this.v6 = ipV6
    }
}
