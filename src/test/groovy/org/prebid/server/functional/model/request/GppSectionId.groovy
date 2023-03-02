package org.prebid.server.functional.model.request

enum GppSectionId {

    TCF_EU_V2("2"), US_PV_V1("6")

    final String value

    GppSectionId(String value) {
        this.value = value
    }

    @Override
    String toString() {
        return value
    }
}
