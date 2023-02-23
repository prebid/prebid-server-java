package org.prebid.server.functional.util.privacy.gpp

import com.iab.gpp.encoder.field.UspV1Field

class UspV1Consent extends GppConsent {

    protected UspV1Consent(Regions regions, def fieldValues) {
        super(regions, fieldValues)
    }

    static class Builder extends GppConsent.Builder {

        Builder() {
            super(GppConsent.Regions.USPV1)
        }

        Builder lspaCovered(String version) {
            fieldValue(UspV1Field.LSPA_COVERED, version)
            this
        }

        Builder optOutSale(String optOutSale) {
            fieldValue(UspV1Field.OPT_OUT_SALE, optOutSale)
            this
        }

        Builder notice(String notice) {
            fieldValue(UspV1Field.NOTICE, notice)
            this
        }

        @Override
        UspV1Consent build() {
            new UspV1Consent(regions, fieldValues)
        }
    }
}
