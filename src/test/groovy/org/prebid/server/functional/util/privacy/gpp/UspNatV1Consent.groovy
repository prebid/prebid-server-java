package org.prebid.server.functional.util.privacy.gpp

import com.iab.gpp.encoder.field.UspNatV1Field
import org.prebid.server.functional.util.privacy.gpp.data.UsNationalSensitiveData

class UspNatV1Consent extends GppConsent {

    protected UspNatV1Consent(Section section, Map<String, Object> fieldValues) {
        super(section, fieldValues)
    }

    @Override
    protected String encodeSection() {
        gppModel.encodeSection(Section.USP_NAT_V1.name)
    }

    static class Builder extends GppConsent.Builder {

        Builder() {
            super(GppConsent.Section.USP_NAT_V1)
        }

        Builder setVersion(Integer version) {
            fieldValue(UspNatV1Field.VERSION, version)
            this
        }

        Builder setSharingNotice(Integer sharingNotice) {
            fieldValue(UspNatV1Field.SHARING_NOTICE, sharingNotice)
            this
        }

        Builder setSaleOptOutNotice(Integer saleOptOutNotice) {
            fieldValue(UspNatV1Field.SALE_OPT_OUT_NOTICE, saleOptOutNotice)
            this
        }

        Builder setSharingOptOutNotice(Integer sharingOptOutNotice) {
            fieldValue(UspNatV1Field.SHARING_OPT_OUT_NOTICE, sharingOptOutNotice)
            this
        }

        Builder setTargetedAdvertisingOptOutNotice(Integer targetedAdvertisingOptOutNotice) {
            fieldValue(UspNatV1Field.TARGETED_ADVERTISING_OPT_OUT_NOTICE, targetedAdvertisingOptOutNotice)
            this
        }

        Builder setSensitiveDataProcessingOptOutNotice(Integer sensitiveDataProcessingOptOutNotice) {
            fieldValue(UspNatV1Field.SENSITIVE_DATA_PROCESSING_OPT_OUT_NOTICE, sensitiveDataProcessingOptOutNotice)
            this
        }

        Builder setSensitiveDataLimitUseNotice(Integer sensitiveDataLimitUseNotice) {
            fieldValue(UspNatV1Field.SENSITIVE_DATA_LIMIT_USE_NOTICE, sensitiveDataLimitUseNotice)
            this
        }

        Builder setSaleOptOut(Integer saleOptOut) {
            fieldValue(UspNatV1Field.SALE_OPT_OUT, saleOptOut)
            this
        }

        Builder setSharingOptOut(Integer sharingOptOut) {
            fieldValue(UspNatV1Field.SHARING_OPT_OUT, sharingOptOut)
            this
        }

        Builder setTargetedAdvertisingOptOut(Integer targetedAdvertisingOptOut) {
            fieldValue(UspNatV1Field.TARGETED_ADVERTISING_OPT_OUT, targetedAdvertisingOptOut)
            this
        }

        Builder setSensitiveDataProcessing(UsNationalSensitiveData sensitiveDataProcessing) {
            fieldValue(UspNatV1Field.SENSITIVE_DATA_PROCESSING, sensitiveDataProcessing.contentList)
            this
        }

        Builder setPersonalDataConsents(Integer personalDataConsents) {
            fieldValue(UspNatV1Field.PERSONAL_DATA_CONSENTS, personalDataConsents)
            this
        }

        Builder setKnownChildSensitiveDataConsents(Integer childFrom13to16, Integer childBlow13) {
            fieldValue(UspNatV1Field.KNOWN_CHILD_SENSITIVE_DATA_CONSENTS, [childFrom13to16, childBlow13])
            this
        }

        Builder setMspaCoveredTransaction(Integer mspaCoveredTransaction) {
            fieldValue(UspNatV1Field.MSPA_COVERED_TRANSACTION, mspaCoveredTransaction)
            this
        }

        Builder setMspaOptOutOptionMode(Integer mspaOptOutOptionMode) {
            fieldValue(UspNatV1Field.MSPA_OPT_OUT_OPTION_MODE, mspaOptOutOptionMode)
            this
        }

        Builder setMspaServiceProviderMode(Integer mspaServiceProviderMode) {
            fieldValue(UspNatV1Field.MSPA_SERVICE_PROVIDER_MODE, mspaServiceProviderMode)
            this
        }

        Builder setGpcSegmentType(Integer gpcSegmentType) {
            fieldValue(UspNatV1Field.GPC_SEGMENT_TYPE, gpcSegmentType)
            this
        }

        Builder setGpcSegmentIncluded(Integer gpcSegmentIncluded) {
            fieldValue(UspNatV1Field.GPC_SEGMENT_INCLUDED, gpcSegmentIncluded)
            this
        }

        Builder setGpc(Boolean gpc) {
            fieldValue(UspNatV1Field.GPC, gpc)
            this
        }

        @Override
        GppConsent build() {
            new UspNatV1Consent(section, fieldValues)
        }
    }
}
