package org.prebid.server.functional.util.privacy.gpp

import com.iab.gpp.encoder.field.UspV1Field

class UspV1Consent extends GppConsent {

    protected UspV1Consent(Section regions, def fieldValues) {
        super(regions, fieldValues)
    }

    static class Builder extends GppConsent.Builder {

        Builder() {
            super(GppConsent.Section.USPV1)
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
        UspV1Consent build() {
            new UspV1Consent(section, fieldValues)
        }
    }
}
