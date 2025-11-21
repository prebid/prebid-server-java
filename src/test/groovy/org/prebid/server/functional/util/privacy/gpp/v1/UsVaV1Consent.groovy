package org.prebid.server.functional.util.privacy.gpp.v1

import com.iab.gpp.encoder.field.UsVaField
import org.prebid.server.functional.model.privacy.gpp.MspaMode
import org.prebid.server.functional.model.privacy.gpp.Notice
import org.prebid.server.functional.model.privacy.gpp.OptOut
import org.prebid.server.functional.model.privacy.gpp.UsVirginiaV1ChildSensitiveData
import org.prebid.server.functional.util.privacy.gpp.GppConsent
import org.prebid.server.functional.model.privacy.gpp.UsVirginiaV1SensitiveData

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

        Builder setSharingNotice(Notice sharingNotice) {
            fieldValue(UsVaField.SHARING_NOTICE, sharingNotice.value)
            this
        }

        Builder setSaleOptOutNotice(Notice saleOptOutNotice) {
            fieldValue(UsVaField.SALE_OPT_OUT_NOTICE, saleOptOutNotice.value)
            this
        }

        Builder setTargetedAdvertisingOptOutNotice(Notice targetedAdvertisingOptOutNotice) {
            fieldValue(UsVaField.TARGETED_ADVERTISING_OPT_OUT_NOTICE, targetedAdvertisingOptOutNotice.value)
            this
        }

        Builder setSaleOptOut(OptOut saleOptOut) {
            fieldValue(UsVaField.SALE_OPT_OUT, saleOptOut.value)
            this
        }

        Builder setTargetedAdvertisingOptOut(OptOut targetedAdvertisingOptOut) {
            fieldValue(UsVaField.TARGETED_ADVERTISING_OPT_OUT, targetedAdvertisingOptOut.value)
            this
        }

        Builder setSensitiveDataProcessing(UsVirginiaV1SensitiveData sensitiveDataProcessing) {
            fieldValue(UsVaField.SENSITIVE_DATA_PROCESSING, sensitiveDataProcessing.contentList)
            this
        }

        Builder setKnownChildSensitiveDataConsents(UsVirginiaV1ChildSensitiveData childSensitiveDataConsents) {
            fieldValue(UsVaField.KNOWN_CHILD_SENSITIVE_DATA_CONSENTS, childSensitiveDataConsents.contentList)
            this
        }

        Builder setMspaCoveredTransaction(MspaMode mspaCoveredTransaction) {
            fieldValue(UsVaField.MSPA_COVERED_TRANSACTION, mspaCoveredTransaction.value)
            this
        }

        Builder setMspaOptOutOptionMode(MspaMode mspaOptOutOptionMode) {
            fieldValue(UsVaField.MSPA_OPT_OUT_OPTION_MODE, mspaOptOutOptionMode.value)
            this
        }

        Builder setMspaServiceProviderMode(MspaMode mspaServiceProviderMode) {
            fieldValue(UsVaField.MSPA_SERVICE_PROVIDER_MODE, mspaServiceProviderMode.value)
            this
        }

        @Override
        GppConsent build() {
            return new UsVaV1Consent(section, fieldValues)
        }
    }
}
