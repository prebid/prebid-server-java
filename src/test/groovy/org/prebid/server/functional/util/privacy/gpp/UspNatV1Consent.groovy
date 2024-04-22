package org.prebid.server.functional.util.privacy.gpp

import com.iab.gpp.encoder.field.UsNatV1Field
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
            fieldValue(UsNatV1Field.VERSION, version)
            this
        }

        Builder setSharingNotice(Integer sharingNotice) {
            fieldValue(UsNatV1Field.SHARING_NOTICE, sharingNotice)
            this
        }

        Builder setSaleOptOutNotice(Integer saleOptOutNotice) {
            fieldValue(UsNatV1Field.SALE_OPT_OUT_NOTICE, saleOptOutNotice)
            this
        }

        Builder setSharingOptOutNotice(Integer sharingOptOutNotice) {
            fieldValue(UsNatV1Field.SHARING_OPT_OUT_NOTICE, sharingOptOutNotice)
            this
        }

        Builder setTargetedAdvertisingOptOutNotice(Integer targetedAdvertisingOptOutNotice) {
            fieldValue(UsNatV1Field.TARGETED_ADVERTISING_OPT_OUT_NOTICE, targetedAdvertisingOptOutNotice)
            this
        }

        Builder setSensitiveDataProcessingOptOutNotice(Integer sensitiveDataProcessingOptOutNotice) {
            fieldValue(UsNatV1Field.SENSITIVE_DATA_PROCESSING_OPT_OUT_NOTICE, sensitiveDataProcessingOptOutNotice)
            this
        }

        Builder setSensitiveDataLimitUseNotice(Integer sensitiveDataLimitUseNotice) {
            fieldValue(UsNatV1Field.SENSITIVE_DATA_LIMIT_USE_NOTICE, sensitiveDataLimitUseNotice)
            this
        }

        Builder setSaleOptOut(Integer saleOptOut) {
            fieldValue(UsNatV1Field.SALE_OPT_OUT, saleOptOut)
            this
        }

        Builder setSharingOptOut(Integer sharingOptOut) {
            fieldValue(UsNatV1Field.SHARING_OPT_OUT, sharingOptOut)
            this
        }

        Builder setTargetedAdvertisingOptOut(Integer targetedAdvertisingOptOut) {
            fieldValue(UsNatV1Field.TARGETED_ADVERTISING_OPT_OUT, targetedAdvertisingOptOut)
            this
        }

        Builder setSensitiveDataProcessing(UsNationalSensitiveData sensitiveDataProcessing) {
            fieldValue(UsNatV1Field.SENSITIVE_DATA_PROCESSING, sensitiveDataProcessing.contentList)
            this
        }

        Builder setPersonalDataConsents(Integer personalDataConsents) {
            fieldValue(UsNatV1Field.PERSONAL_DATA_CONSENTS, personalDataConsents)
            this
        }

        Builder setKnownChildSensitiveDataConsents(Integer childFrom13to16, Integer childBlow13) {
            fieldValue(UsNatV1Field.KNOWN_CHILD_SENSITIVE_DATA_CONSENTS, [childFrom13to16, childBlow13])
            this
        }

        Builder setMspaCoveredTransaction(Integer mspaCoveredTransaction) {
            fieldValue(UsNatV1Field.MSPA_COVERED_TRANSACTION, mspaCoveredTransaction)
            this
        }

        Builder setMspaOptOutOptionMode(Integer mspaOptOutOptionMode) {
            fieldValue(UsNatV1Field.MSPA_OPT_OUT_OPTION_MODE, mspaOptOutOptionMode)
            this
        }

        Builder setMspaServiceProviderMode(Integer mspaServiceProviderMode) {
            fieldValue(UsNatV1Field.MSPA_SERVICE_PROVIDER_MODE, mspaServiceProviderMode)
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
            new UsNatV1Consent(section, fieldValues)
        }
    }
}
