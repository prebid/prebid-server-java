package org.prebid.server.functional.util

class UsPrivacy {

    private static final String specificationVersion = "1"
    private Signal explicitNotice
    private Signal optOutSale
    private Signal serviceProviderAgreement

    String getUsPrivacy() {
        "$specificationVersion$explicitNotice.value$optOutSale.value$serviceProviderAgreement.value"
    }

    enum Signal {

        ENFORCED("Y"),
        NOT_ENFORCED("N"),
        NOT_DEFINED("-")

        final String value

        Signal(String value) {
            this.value = value
        }
    }
}
