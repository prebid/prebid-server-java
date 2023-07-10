package org.prebid.server.functional.util.privacy.gpp

import com.iab.gpp.encoder.section.EncodableSection
import org.prebid.server.functional.util.privacy.gpp.data.UsNationalSensitiveData

abstract class UsConsent extends GppConsent {

    protected UsConsent(Section section, Map<String, Object> fieldValues) {
        super(section, fieldValues)
    }

    abstract UspNatV1Consent normaliseToNational()

    abstract protected UsNationalSensitiveData normaliseSensitiveData(EncodableSection encodableSection)

    abstract protected List<Integer> normalizeChildConsents(EncodableSection encodableSection)
}
