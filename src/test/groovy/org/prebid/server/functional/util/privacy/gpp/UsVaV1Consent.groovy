package org.prebid.server.functional.util.privacy.gpp

import com.iab.gpp.encoder.field.UsVaField
import org.prebid.server.functional.util.privacy.gpp.data.UsVirginiaSensitiveData

class UsVaV1Consent extends GppConsent {

    private static final Section SECTION = Section.US_VA_V1

    protected UsVaV1Consent(Section section, Map<String, Object> fieldValues) {
        super(section, fieldValues)
    }

    @Override
    protected String encodeSection() {
        gppModel.encodeSection(SECTION.name)
    }

    static class Builder extends GppConsent.Builder {

        Builder() {
            super(SECTION)
        }

        Builder setVersion(Integer version) {
            fieldValue(UsVaField.VERSION, version)
            this
        }

        Builder setSharingNotice(Integer sharingNotice) {
            fieldValue(UsVaField.SHARING_NOTICE, sharingNotice)
            this
        }

        Builder setSaleOptOutNotice(Integer saleOptOutNotice) {
            fieldValue(UsVaField.SALE_OPT_OUT_NOTICE, saleOptOutNotice)
            this
        }

        Builder setTargetedAdvertisingOptOutNotice(Integer targetedAdvertisingOptOutNotice) {
            fieldValue(UsVaField.TARGETED_ADVERTISING_OPT_OUT_NOTICE, targetedAdvertisingOptOutNotice)
            this
        }

        Builder setSaleOptOut(Integer saleOptOut) {
            fieldValue(UsVaField.SALE_OPT_OUT, saleOptOut)
            this
        }

        Builder setTargetedAdvertisingOptOut(Integer targetedAdvertisingOptOut) {
            fieldValue(UsVaField.TARGETED_ADVERTISING_OPT_OUT, targetedAdvertisingOptOut)
            this
        }

        Builder setSensitiveDataProcessing(UsVirginiaSensitiveData sensitiveDataProcessing) {
            fieldValue(UsVaField.SENSITIVE_DATA_PROCESSING, sensitiveDataProcessing.contentList)
            this
        }

        Builder setKnownChildSensitiveDataConsents(Integer childSensitiveDataConsents) {
            fieldValue(UsVaField.KNOWN_CHILD_SENSITIVE_DATA_CONSENTS, childSensitiveDataConsents)
            this
        }

        Builder setMspaCoveredTransaction(Integer mspaCoveredTransaction) {
            fieldValue(UsVaField.MSPA_COVERED_TRANSACTION, mspaCoveredTransaction)
            this
        }

        Builder setMspaOptOutOptionMode(Integer mspaOptOutOptionMode) {
            fieldValue(UsVaField.MSPA_OPT_OUT_OPTION_MODE, mspaOptOutOptionMode)
            this
        }

        Builder setMspaServiceProviderMode(Integer mspaServiceProviderMode) {
            fieldValue(UsVaField.MSPA_SERVICE_PROVIDER_MODE, mspaServiceProviderMode)
            this
        }

        @Override
        GppConsent build() {
            return new UsVaV1Consent(section, fieldValues)
        }
    }
}
