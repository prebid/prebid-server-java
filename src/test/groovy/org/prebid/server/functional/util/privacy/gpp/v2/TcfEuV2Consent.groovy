package org.prebid.server.functional.util.privacy.gpp.v2

import com.iab.gpp.encoder.field.TcfEuV2Field
import org.prebid.server.functional.util.privacy.gpp.GppConsent

class TcfEuV2Consent extends GppConsent {

    protected TcfEuV2Consent(Section section, Map<String, Object> fieldValues) {
        super(section, fieldValues)
    }

    @Override
    String encodeSection() {
        gppModel.encodeSection(Section.TCF_EU_V2.name)
    }

    static class Builder extends GppConsent.Builder {

        Builder() {
            super(GppConsent.Section.TCF_EU_V2)
        }

        Builder setPolicyVersion(int version) {
            fieldValue(TcfEuV2Field.POLICY_VERSION, version)
            this
        }

        Builder setVendorListVersion(int version) {
            fieldValue(TcfEuV2Field.VENDOR_LIST_VERSION, version)
            this
        }

        Builder setPurposesConsent(List<Boolean> purposesConsent) {
            fieldValue(TcfEuV2Field.PURPOSE_CONSENTS, purposesConsent)
            this
        }

        Builder setVendorConsent(List<Integer> purposesConsent) {
            fieldValue(TcfEuV2Field.VENDOR_CONSENTS, purposesConsent)
            this
        }

        Builder setVendorLegitimateInterest(List<Integer> vendorLegitimateInterest) {
            fieldValue(TcfEuV2Field.VENDOR_LEGITIMATE_INTERESTS, vendorLegitimateInterest)
            this
        }

        Builder setPurposesLITransparency(List<Boolean> purposesConsent) {
            fieldValue(TcfEuV2Field.PURPOSE_LEGITIMATE_INTERESTS, purposesConsent)
            this
        }

        @Override
        TcfEuV2Consent build() {
            new TcfEuV2Consent(section, fieldValues)
        }
    }
}
