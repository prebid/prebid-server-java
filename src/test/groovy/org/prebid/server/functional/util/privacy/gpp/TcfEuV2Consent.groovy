package org.prebid.server.functional.util.privacy.gpp

import com.iab.gpp.encoder.datatype.EncodableFixedBitfield
import com.iab.gpp.encoder.datatype.EncodableOptimizedFixedRange
import com.iab.gpp.encoder.field.TcfEuV2Field

class TcfEuV2Consent extends GppConsent {

    public static List<Boolean> EMPTY_CONSENT = Collections.nCopies(24, false)

    protected TcfEuV2Consent(Section regions, def fieldValues) {
        super(regions, fieldValues)
    }

    static class Builder extends GppConsent.Builder {

        Builder() {
            super(GppConsent.Section.TCFEUV2)
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
            fieldValue(TcfEuV2Field.PURPOSE_CONSENTS, new EncodableFixedBitfield(purposesConsent))
            this
        }

        Builder setVendorConsent(List<Boolean> purposesConsent) {
            fieldValue(TcfEuV2Field.VENDOR_CONSENTS, new EncodableFixedBitfield(purposesConsent))
            this
        }

        Builder setVendorLegitimateInterest(List<Integer> vendorLegitimateInterest) {
            fieldValue(TcfEuV2Field.VENDOR_LEGITIMATE_INTERESTS, new EncodableOptimizedFixedRange(vendorLegitimateInterest))
            this
        }

        Builder setPurposesLITransparency(List<Boolean> purposesConsent) {
            fieldValue(TcfEuV2Field.PURPOSE_LEGITIMATE_INTERESTS, new EncodableFixedBitfield(purposesConsent))
            this
        }

        @Override
        TcfEuV2Consent build() {
            new TcfEuV2Consent(section, fieldValues)
        }
    }
}
