package org.prebid.server.functional.util.privacy

import com.iab.gpp.encoder.GppModel
import com.iab.gpp.encoder.section.TcfEuV2

class GppConsent implements ConsentString {

    private final GppModel gppModel

    GppConsent() {
        gppModel = new GppModel()
    }

    GppConsent setFieldValue(String sectionName,
                             String fieldName = "ConsentLanguage",
                             Object value = "EN") {
        gppModel.setFieldValue(sectionName, fieldName, value)
        this
    }

    @Override
    String getConsentString() {
        gppModel.encode()
    }

    @Override
    String toString() {
        consentString
    }
}
