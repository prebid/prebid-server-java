package org.prebid.server.functional.util.privacy.gpp

import com.iab.gpp.encoder.field.UsUtV1Field
import org.prebid.server.functional.util.privacy.gpp.data.UsUtahSensitiveData

class UsUtV1Consent extends GppConsent {

    private static final Section SECTION = Section.USP_UT_V1

    protected UsUtV1Consent(Section section, Map<String, Object> fieldValues) {
        super(section, fieldValues)
    }

    @Override
    protected String encodeSection() {
        gppModel.encodeSection(SECTION.name)
    }

    static class Builder extends GppConsent.Builder {

        Builder() {
            super(GppConsent.Section.USP_UT_V1)
        }

        Builder setVersion(Integer version) {
            fieldValue(UsUtV1Field.VERSION, version)
            this
        }

        Builder setSharingNotice(Integer sharingNotice) {
            fieldValue(UsUtV1Field.SHARING_NOTICE, sharingNotice)
            this
        }

        Builder setSaleOptOutNotice(Integer saleOptOutNotice) {
            fieldValue(UsUtV1Field.SALE_OPT_OUT_NOTICE, saleOptOutNotice)
            this
        }

        Builder setTargetedAdvertisingOptOutNotice(Integer targetedAdvertisingOptOutNotice) {
            fieldValue(UsUtV1Field.TARGETED_ADVERTISING_OPT_OUT_NOTICE, targetedAdvertisingOptOutNotice)
            this
        }

        Builder setSensitiveDataProcessingOptOutNotice(Integer sensitiveDataProcessingOptOutNotice) {
            fieldValue(UsUtV1Field.SENSITIVE_DATA_PROCESSING_OPT_OUT_NOTICE, sensitiveDataProcessingOptOutNotice)
            this
        }

        Builder setSaleOptOut(Integer saleOptOut) {
            fieldValue(UsUtV1Field.SALE_OPT_OUT, saleOptOut)
            this
        }

        Builder setTargetedAdvertisingOptOut(Integer targetedAdvertisingOptOut) {
            fieldValue(UsUtV1Field.TARGETED_ADVERTISING_OPT_OUT, targetedAdvertisingOptOut)
            this
        }

        Builder setSensitiveDataProcessing(UsUtahSensitiveData sensitiveDataProcessing) {
            fieldValue(UsUtV1Field.SENSITIVE_DATA_PROCESSING, sensitiveDataProcessing.contentList)
            this
        }

        Builder setKnownChildSensitiveDataConsents(Integer knownChildSensitiveDataConsents) {
            fieldValue(UsUtV1Field.KNOWN_CHILD_SENSITIVE_DATA_CONSENTS, knownChildSensitiveDataConsents)
            this
        }

        Builder setMspaCoveredTransaction(Integer mspaCoveredTransaction) {
            fieldValue(UsUtV1Field.MSPA_COVERED_TRANSACTION, mspaCoveredTransaction)
            this
        }

        Builder setMspaOptOutOptionMode(Integer mspaOptOutOptionMode) {
            fieldValue(UsUtV1Field.MSPA_OPT_OUT_OPTION_MODE, mspaOptOutOptionMode)
            this
        }

        Builder setMspaServiceProviderMode(Integer mspaServiceProviderMode) {
            fieldValue(UsUtV1Field.MSPA_SERVICE_PROVIDER_MODE, mspaServiceProviderMode)
            this
        }

        @Override
        GppConsent build() {
            return new UsUtV1Consent(section, fieldValues)
        }
    }
}
