package org.prebid.server.functional.util.privacy

import org.prebid.server.functional.util.PBSUtils

class BogusConsent implements BuildableConsentString {

    private static final String bogusConsentString = PBSUtils.randomString

    @Override
    String getConsentString() {
        bogusConsentString
    }

    @Override
    String toString() {
        consentString
    }
}
