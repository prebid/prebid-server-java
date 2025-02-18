package org.prebid.server.functional.util.privacy.gpp.v2

import com.iab.gpp.encoder.field.UsNatV1Field
import org.prebid.server.functional.model.privacy.gpp.DataActivity
import org.prebid.server.functional.model.privacy.gpp.MspaMode
import org.prebid.server.functional.model.privacy.gpp.Notice
import org.prebid.server.functional.model.privacy.gpp.OptOut
import org.prebid.server.functional.model.privacy.gpp.UsNationalV2ChildSensitiveData
import org.prebid.server.functional.util.privacy.gpp.GppConsent
import org.prebid.server.functional.model.privacy.gpp.UsNationalV1SensitiveData

class UsNatV2Consent extends GppConsent {

    private static final Section SECTION = Section.US_NAT_V2

    protected UsNatV2Consent(Section section, Map<String, Object> fieldValues) {
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
            fieldValue(UsNatV1Field.SHARING_NOTICE, sharingNotice.value)
            this
        }

        Builder setSaleOptOutNotice(Notice saleOptOutNotice) {
            fieldValue(UsNatV1Field.SALE_OPT_OUT_NOTICE, saleOptOutNotice.value)
            this
        }

        Builder setSharingOptOutNotice(Notice sharingOptOutNotice) {
            fieldValue(UsNatV1Field.SHARING_OPT_OUT_NOTICE, sharingOptOutNotice.value)
            this
        }

        Builder setTargetedAdvertisingOptOutNotice(Notice targetedAdvertisingOptOutNotice) {
            fieldValue(UsNatV1Field.TARGETED_ADVERTISING_OPT_OUT_NOTICE, targetedAdvertisingOptOutNotice.value)
            this
        }

        Builder setSensitiveDataProcessingOptOutNotice(Notice sensitiveDataProcessingOptOutNotice) {
            fieldValue(UsNatV1Field.SENSITIVE_DATA_PROCESSING_OPT_OUT_NOTICE, sensitiveDataProcessingOptOutNotice.value)
            this
        }

        Builder setSensitiveDataLimitUseNotice(Notice sensitiveDataLimitUseNotice) {
            fieldValue(UsNatV1Field.SENSITIVE_DATA_LIMIT_USE_NOTICE, sensitiveDataLimitUseNotice.value)
            this
        }

        Builder setSaleOptOut(OptOut saleOptOut) {
            fieldValue(UsNatV1Field.SALE_OPT_OUT, saleOptOut.value)
            this
        }

        Builder setSharingOptOut(OptOut sharingOptOut) {
            fieldValue(UsNatV1Field.SHARING_OPT_OUT, sharingOptOut.value)
            this
        }

        Builder setTargetedAdvertisingOptOut(OptOut targetedAdvertisingOptOut) {
            fieldValue(UsNatV1Field.TARGETED_ADVERTISING_OPT_OUT, targetedAdvertisingOptOut.value)
            this
        }

        Builder setSensitiveDataProcessing(UsNationalV1SensitiveData sensitiveDataProcessing) {
            fieldValue(UsNatV1Field.SENSITIVE_DATA_PROCESSING, sensitiveDataProcessing.contentList)
            this
        }

        Builder setPersonalDataConsents(DataActivity personalDataConsents) {
            fieldValue(UsNatV1Field.PERSONAL_DATA_CONSENTS, personalDataConsents.value)
            this
        }

        Builder setKnownChildSensitiveDataConsents(UsNationalV2ChildSensitiveData childSensitiveData) {
            fieldValue(UsNatV1Field.KNOWN_CHILD_SENSITIVE_DATA_CONSENTS, childSensitiveData.contentList)
            this
        }

        Builder setMspaCoveredTransaction(MspaMode mspaCoveredTransaction) {
            fieldValue(UsNatV1Field.MSPA_COVERED_TRANSACTION, mspaCoveredTransaction.value)
            this
        }

        Builder setMspaOptOutOptionMode(MspaMode mspaOptOutOptionMode) {
            fieldValue(UsNatV1Field.MSPA_OPT_OUT_OPTION_MODE, mspaOptOutOptionMode.value)
            this
        }

        Builder setMspaServiceProviderMode(MspaMode mspaServiceProviderMode) {
            fieldValue(UsNatV1Field.MSPA_SERVICE_PROVIDER_MODE, mspaServiceProviderMode.value)
            this
        }

        Builder setGpcSegmentType(Integer gpcSegmentType) {
            fieldValue(UsNatV1Field.GPC_SEGMENT_TYPE, gpcSegmentType)
            this
        }

        Builder setGpcSegmentIncluded(Boolean gpcSegmentIncluded) {
            fieldValue(UsNatV1Field.GPC_SEGMENT_INCLUDED, gpcSegmentIncluded)
            this
        }

        Builder setGpc(Boolean gpc) {
            fieldValue(UsNatV1Field.GPC, gpc)
            this
        }

        @Override
        GppConsent build() {
            new UsNatV2Consent(section, fieldValues)
        }
    }
}
