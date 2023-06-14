package org.prebid.server.functional.util.privacy.gpp

import com.iab.gpp.encoder.field.UspV1Field

import static org.prebid.server.functional.util.privacy.CcpaConsent.Signal

class UspV1Consent extends GppConsent {

    protected UspV1Consent(Section section, Map<String, Object> fieldValues) {
        super(section, fieldValues)
    }

    @Override
    String encodeSection() {
        gppModel.encodeSection(Section.USP_V1.name)
    }

    static class Builder extends GppConsent.Builder {

        Builder() {
            super(GppConsent.Section.USP_V1)
        }

        Builder setLspaCovered(String version) {
            fieldValue(UspV1Field.LSPA_COVERED, version)
            this
        }

        Builder setOptOutSale(Signal signal) {
            fieldValue(UspV1Field.OPT_OUT_SALE, signal.value)
            this
        }

        Builder setNotice(Signal signal) {
            fieldValue(UspV1Field.NOTICE, signal.value)
            this
        }

        @Override
        UspV1Consent build() {
            new UspV1Consent(section, fieldValues)
        }
    }
}
