package org.prebid.server.functional.util.privacy

import org.prebid.server.functional.util.PBSUtils

class BogusConsent implements ConsentString {

    private static final String BOGUS_CONSENT_STRING = PBSUtils.randomString

    @Override
    String getConsentString() {
        BOGUS_CONSENT_STRING
    }

    @Override
    String toString() {
        consentString
    }
}
