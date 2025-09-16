package org.prebid.server.functional.util.privacy

import static org.prebid.server.functional.util.privacy.CcpaConsent.Signal.NOT_DEFINED

class CcpaConsent implements ConsentString {

    private static final int SPECIFICATION_VERSION = 1
    private Signal explicitNotice = NOT_DEFINED
    private Signal optOutSale = NOT_DEFINED
    private Signal serviceProviderAgreement = NOT_DEFINED

    @Override
    String getConsentString() {
        "$SPECIFICATION_VERSION$explicitNotice.value$optOutSale.value$serviceProviderAgreement.value"
    }

    @Override
    String toString() {
        consentString
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
