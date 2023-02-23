package org.prebid.server.functional.util.privacy.gpp

import com.iab.gpp.encoder.field.TcfEuV2Field

class TcfEuV2Consent extends GppConsent {

    protected TcfEuV2Consent(Regions regions, def fieldValues) {
        super(regions, fieldValues)
    }

    static class Builder extends GppConsent.Builder {

        Builder() {
            super(GppConsent.Regions.TCFEUV2)
        }

        Builder policyVersion(int version) {
            fieldValue(TcfEuV2Field.POLICY_VERSION, version)
            this
        }

        Builder vendorListVersion(int version) {
            fieldValue(TcfEuV2Field.VENDOR_LIST_VERSION, version)
            this
        }

        @Override
        TcfEuV2Consent build() {
            new TcfEuV2Consent(regions, fieldValues)
        }
    }
}
