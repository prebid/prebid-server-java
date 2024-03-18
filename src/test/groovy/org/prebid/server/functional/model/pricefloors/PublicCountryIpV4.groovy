package org.prebid.server.functional.model.pricefloors

enum PublicCountryIpV4 {

    USA("209.232.44.21"),
    UKR("193.238.111.14"),
    CAN("70.71.245.39")

    String ipV4

    PublicCountryIpV4(String ipV4) {
        this.ipV4 = ipV4
    }
}
