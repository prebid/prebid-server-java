package org.prebid.server.functional.util.privacy.gpp

import com.iab.gpp.encoder.field.UspV1Field

class UsV1Consent extends GppConsent {

    protected UsV1Consent(Section section, Map<String, Object> fieldValues) {
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

        Builder setOptOutSale(String optOutSale) {
            fieldValue(UspV1Field.OPT_OUT_SALE, optOutSale)
            this
        }

        Builder setNotice(String notice) {
            fieldValue(UspV1Field.NOTICE, notice)
            this
        }

        @Override
        UsV1Consent build() {
            new UsV1Consent(section, fieldValues)
        }
    }
}
