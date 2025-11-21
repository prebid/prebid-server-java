package org.prebid.server.functional.util.privacy.gpp.v1

import com.iab.gpp.encoder.field.UsNatField
import org.prebid.server.functional.model.privacy.gpp.DataActivity
import org.prebid.server.functional.model.privacy.gpp.MspaMode
import org.prebid.server.functional.model.privacy.gpp.Notice
import org.prebid.server.functional.model.privacy.gpp.OptOut
import org.prebid.server.functional.model.privacy.gpp.UsNationalV1ChildSensitiveData
import org.prebid.server.functional.util.privacy.gpp.GppConsent
import org.prebid.server.functional.model.privacy.gpp.UsNationalV1SensitiveData

class UsNatV1Consent extends GppConsent {

    private static final Section SECTION = Section.US_NAT_V1

    protected UsNatV1Consent(Section section, Map<String, Object> fieldValues) {
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
            fieldValue(UsNatField.SHARING_NOTICE, sharingNotice.value)
            this
        }

        Builder setSaleOptOutNotice(Notice saleOptOutNotice) {
            fieldValue(UsNatField.SALE_OPT_OUT_NOTICE, saleOptOutNotice.value)
            this
        }

        Builder setSharingOptOutNotice(Notice sharingOptOutNotice) {
            fieldValue(UsNatField.SHARING_OPT_OUT_NOTICE, sharingOptOutNotice.value)
            this
        }

        Builder setTargetedAdvertisingOptOutNotice(Notice targetedAdvertisingOptOutNotice) {
            fieldValue(UsNatField.TARGETED_ADVERTISING_OPT_OUT_NOTICE, targetedAdvertisingOptOutNotice.value)
            this
        }

        Builder setSensitiveDataProcessingOptOutNotice(Notice sensitiveDataProcessingOptOutNotice) {
            fieldValue(UsNatField.SENSITIVE_DATA_PROCESSING_OPT_OUT_NOTICE, sensitiveDataProcessingOptOutNotice.value)
            this
        }

        Builder setSensitiveDataLimitUseNotice(Notice sensitiveDataLimitUseNotice) {
            fieldValue(UsNatField.SENSITIVE_DATA_LIMIT_USE_NOTICE, sensitiveDataLimitUseNotice.value)
            this
        }

        Builder setSaleOptOut(OptOut saleOptOut) {
            fieldValue(UsNatField.SALE_OPT_OUT, saleOptOut.value)
            this
        }

        Builder setSharingOptOut(OptOut sharingOptOut) {
            fieldValue(UsNatField.SHARING_OPT_OUT, sharingOptOut.value)
            this
        }

        Builder setTargetedAdvertisingOptOut(OptOut targetedAdvertisingOptOut) {
            fieldValue(UsNatField.TARGETED_ADVERTISING_OPT_OUT, targetedAdvertisingOptOut.value)
            this
        }

        Builder setSensitiveDataProcessing(UsNationalV1SensitiveData sensitiveDataProcessing) {
            fieldValue(UsNatField.SENSITIVE_DATA_PROCESSING, sensitiveDataProcessing.contentList)
            this
        }

        Builder setPersonalDataConsents(DataActivity personalDataConsents) {
            fieldValue(UsNatField.PERSONAL_DATA_CONSENTS, personalDataConsents.value)
            this
        }

        Builder setKnownChildSensitiveDataConsents(UsNationalV1ChildSensitiveData sensitiveData) {
            fieldValue(UsNatField.KNOWN_CHILD_SENSITIVE_DATA_CONSENTS, sensitiveData.contentList)
            this
        }

        Builder setMspaCoveredTransaction(MspaMode mspaCoveredTransaction) {
            fieldValue(UsNatField.MSPA_COVERED_TRANSACTION, mspaCoveredTransaction.value)
            this
        }

        Builder setMspaOptOutOptionMode(MspaMode mspaOptOutOptionMode) {
            fieldValue(UsNatField.MSPA_OPT_OUT_OPTION_MODE, mspaOptOutOptionMode.value)
            this
        }

        Builder setMspaServiceProviderMode(MspaMode mspaServiceProviderMode) {
            fieldValue(UsNatField.MSPA_SERVICE_PROVIDER_MODE, mspaServiceProviderMode.value)
            this
        }

        Builder setGpcSegmentType(Integer gpcSegmentType) {
            fieldValue(UsNatField.GPC_SEGMENT_TYPE, gpcSegmentType)
            this
        }

        Builder setGpcSegmentIncluded(Boolean gpcSegmentIncluded) {
            fieldValue(UsNatField.GPC_SEGMENT_INCLUDED, gpcSegmentIncluded)
            this
        }

        Builder setGpc(Boolean gpc) {
            fieldValue(UsNatField.GPC, gpc)
            this
        }

        @Override
        GppConsent build() {
            new UsNatV1Consent(section, fieldValues)
        }
    }
}
