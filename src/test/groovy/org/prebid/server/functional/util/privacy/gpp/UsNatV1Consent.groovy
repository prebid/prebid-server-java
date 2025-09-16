package org.prebid.server.functional.util.privacy.gpp

import com.iab.gpp.encoder.field.UsNatField
import org.prebid.server.functional.util.privacy.gpp.data.UsNationalSensitiveData

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

        Builder setVersion(Integer version) {
            fieldValue(UsNatField.VERSION, version)
            this
        }

        Builder setSharingNotice(Integer sharingNotice) {
            fieldValue(UsNatField.SHARING_NOTICE, sharingNotice)
            this
        }

        Builder setSaleOptOutNotice(Integer saleOptOutNotice) {
            fieldValue(UsNatField.SALE_OPT_OUT_NOTICE, saleOptOutNotice)
            this
        }

        Builder setSharingOptOutNotice(Integer sharingOptOutNotice) {
            fieldValue(UsNatField.SHARING_OPT_OUT_NOTICE, sharingOptOutNotice)
            this
        }

        Builder setTargetedAdvertisingOptOutNotice(Integer targetedAdvertisingOptOutNotice) {
            fieldValue(UsNatField.TARGETED_ADVERTISING_OPT_OUT_NOTICE, targetedAdvertisingOptOutNotice)
            this
        }

        Builder setSensitiveDataProcessingOptOutNotice(Integer sensitiveDataProcessingOptOutNotice) {
            fieldValue(UsNatField.SENSITIVE_DATA_PROCESSING_OPT_OUT_NOTICE, sensitiveDataProcessingOptOutNotice)
            this
        }

        Builder setSensitiveDataLimitUseNotice(Integer sensitiveDataLimitUseNotice) {
            fieldValue(UsNatField.SENSITIVE_DATA_LIMIT_USE_NOTICE, sensitiveDataLimitUseNotice)
            this
        }

        Builder setSaleOptOut(Integer saleOptOut) {
            fieldValue(UsNatField.SALE_OPT_OUT, saleOptOut)
            this
        }

        Builder setSharingOptOut(Integer sharingOptOut) {
            fieldValue(UsNatField.SHARING_OPT_OUT, sharingOptOut)
            this
        }

        Builder setTargetedAdvertisingOptOut(Integer targetedAdvertisingOptOut) {
            fieldValue(UsNatField.TARGETED_ADVERTISING_OPT_OUT, targetedAdvertisingOptOut)
            this
        }

        Builder setSensitiveDataProcessing(UsNationalSensitiveData sensitiveDataProcessing) {
            fieldValue(UsNatField.SENSITIVE_DATA_PROCESSING, sensitiveDataProcessing.contentList)
            this
        }

        Builder setPersonalDataConsents(Integer personalDataConsents) {
            fieldValue(UsNatField.PERSONAL_DATA_CONSENTS, personalDataConsents)
            this
        }

        Builder setKnownChildSensitiveDataConsents(Integer childFrom13to16, Integer childBlow13) {
            fieldValue(UsNatField.KNOWN_CHILD_SENSITIVE_DATA_CONSENTS, [childFrom13to16, childBlow13])
            this
        }

        Builder setMspaCoveredTransaction(Integer mspaCoveredTransaction) {
            fieldValue(UsNatField.MSPA_COVERED_TRANSACTION, mspaCoveredTransaction)
            this
        }

        Builder setMspaOptOutOptionMode(Integer mspaOptOutOptionMode) {
            fieldValue(UsNatField.MSPA_OPT_OUT_OPTION_MODE, mspaOptOutOptionMode)
            this
        }

        Builder setMspaServiceProviderMode(Integer mspaServiceProviderMode) {
            fieldValue(UsNatField.MSPA_SERVICE_PROVIDER_MODE, mspaServiceProviderMode)
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
