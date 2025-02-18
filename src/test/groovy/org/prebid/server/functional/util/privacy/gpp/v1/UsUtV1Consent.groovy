package org.prebid.server.functional.util.privacy.gpp.v1

import com.iab.gpp.encoder.field.UsUtV1Field
import org.prebid.server.functional.model.privacy.gpp.MspaMode
import org.prebid.server.functional.model.privacy.gpp.Notice
import org.prebid.server.functional.model.privacy.gpp.OptOut
import org.prebid.server.functional.model.privacy.gpp.UsUtahV1ChildSensitiveData
import org.prebid.server.functional.util.privacy.gpp.GppConsent
import org.prebid.server.functional.model.privacy.gpp.UsUtahV1SensitiveData

class UsUtV1Consent extends GppConsent {

    private static final Section SECTION = Section.US_UT_V1

    protected UsUtV1Consent(Section section, Map<String, Object> fieldValues) {
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
            fieldValue(UsUtV1Field.SHARING_NOTICE, sharingNotice.value)
            this
        }

        Builder setSaleOptOutNotice(Notice saleOptOutNotice) {
            fieldValue(UsUtV1Field.SALE_OPT_OUT_NOTICE, saleOptOutNotice.value)
            this
        }

        Builder setTargetedAdvertisingOptOutNotice(Notice targetedAdvertisingOptOutNotice) {
            fieldValue(UsUtV1Field.TARGETED_ADVERTISING_OPT_OUT_NOTICE, targetedAdvertisingOptOutNotice.value)
            this
        }

        Builder setSensitiveDataProcessingOptOutNotice(Notice sensitiveDataProcessingOptOutNotice) {
            fieldValue(UsUtV1Field.SENSITIVE_DATA_PROCESSING_OPT_OUT_NOTICE, sensitiveDataProcessingOptOutNotice.value)
            this
        }

        Builder setSaleOptOut(OptOut saleOptOut) {
            fieldValue(UsUtV1Field.SALE_OPT_OUT, saleOptOut.value)
            this
        }

        Builder setTargetedAdvertisingOptOut(OptOut targetedAdvertisingOptOut) {
            fieldValue(UsUtV1Field.TARGETED_ADVERTISING_OPT_OUT, targetedAdvertisingOptOut.value)
            this
        }

        Builder setSensitiveDataProcessing(UsUtahV1SensitiveData sensitiveDataProcessing) {
            fieldValue(UsUtV1Field.SENSITIVE_DATA_PROCESSING, sensitiveDataProcessing.contentList)
            this
        }

        Builder setKnownChildSensitiveDataConsents(UsUtahV1ChildSensitiveData knownChildSensitiveDataConsents) {
            fieldValue(UsUtV1Field.KNOWN_CHILD_SENSITIVE_DATA_CONSENTS, knownChildSensitiveDataConsents.contentList)
            this
        }

        Builder setMspaCoveredTransaction(MspaMode mspaCoveredTransaction) {
            fieldValue(UsUtV1Field.MSPA_COVERED_TRANSACTION, mspaCoveredTransaction.value)
            this
        }

        Builder setMspaOptOutOptionMode(MspaMode mspaOptOutOptionMode) {
            fieldValue(UsUtV1Field.MSPA_OPT_OUT_OPTION_MODE, mspaOptOutOptionMode.value)
            this
        }

        Builder setMspaServiceProviderMode(MspaMode mspaServiceProviderMode) {
            fieldValue(UsUtV1Field.MSPA_SERVICE_PROVIDER_MODE, mspaServiceProviderMode.value)
            this
        }

        @Override
        GppConsent build() {
            return new UsUtV1Consent(section, fieldValues)
        }
    }
}
