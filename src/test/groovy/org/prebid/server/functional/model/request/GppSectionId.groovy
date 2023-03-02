package org.prebid.server.functional.model.request

enum GppSectionId {

    TCF_EU_V2("2"), USP_V1("6")

    final String value

    GppSectionId(String value) {
        this.value = value
    }

    int getValueAsInt(){
        value.toInteger()
    }
}
