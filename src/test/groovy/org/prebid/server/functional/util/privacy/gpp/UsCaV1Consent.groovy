package org.prebid.server.functional.util.privacy.gpp

import com.iab.gpp.encoder.field.UsCaField
import org.prebid.server.functional.util.privacy.gpp.data.UsCaliforniaSensitiveData

class UsCaV1Consent extends GppConsent {

    private static final Section SECTION = Section.US_CA_V1

    protected UsCaV1Consent(Section section, Map<String, Object> fieldValues) {
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
            fieldValue(UsCaField.VERSION, version)
            this
        }

        Builder setSaleOptOutNotice(Integer saleOptOutNotice) {
            fieldValue(UsCaField.SALE_OPT_OUT_NOTICE, saleOptOutNotice)
            this
        }

        Builder setSharingOptOutNotice(Integer sharingOptOutNotice) {
            fieldValue(UsCaField.SHARING_OPT_OUT_NOTICE, sharingOptOutNotice)
            this
        }

        Builder setSensitiveDataLimitUseNotice(Integer sensitiveDataLimitUseNotice) {
            fieldValue(UsCaField.SENSITIVE_DATA_LIMIT_USE_NOTICE, sensitiveDataLimitUseNotice)
            this
        }

        Builder setSaleOptOut(Integer saleOptOut) {
            fieldValue(UsCaField.SALE_OPT_OUT, saleOptOut)
            this
        }

        Builder setSharingOptOut(Integer sharingOptOut) {
            fieldValue(UsCaField.SHARING_OPT_OUT, sharingOptOut)
            this
        }

        Builder setSensitiveDataProcessing(UsCaliforniaSensitiveData sensitiveDataProcessing) {
            fieldValue(UsCaField.SENSITIVE_DATA_PROCESSING, sensitiveDataProcessing.contentList)
            this
        }

        Builder setKnownChildSensitiveDataConsents(Integer childFrom13to16, Integer childBlow13) {
            fieldValue(UsCaField.KNOWN_CHILD_SENSITIVE_DATA_CONSENTS, [childFrom13to16, childBlow13])
            this
        }

        Builder setPersonalDataConsents(Integer personalDataConsents) {
            fieldValue(UsCaField.PERSONAL_DATA_CONSENTS, personalDataConsents)
            this
        }

        Builder setMspaCoveredTransaction(Integer mspaCoveredTransaction) {
            fieldValue(UsCaField.MSPA_COVERED_TRANSACTION, mspaCoveredTransaction)
            this
        }

        Builder setMspaOptOutOptionMode(Integer mspaOptOutOptionMode) {
            fieldValue(UsCaField.MSPA_OPT_OUT_OPTION_MODE, mspaOptOutOptionMode)
            this
        }

        Builder setMspaServiceProviderMode(Integer mspaServiceProviderMode) {
            fieldValue(UsCaField.MSPA_SERVICE_PROVIDER_MODE, mspaServiceProviderMode)
            this
        }

        Builder setGpcSegmentType(Integer gpcSegmentType) {
            fieldValue(UsCaField.GPC_SEGMENT_TYPE, gpcSegmentType)
            this
        }

        Builder setGpcSegmentIncluded(Boolean gpcSegmentIncluded) {
            fieldValue(UsCaField.GPC_SEGMENT_INCLUDED, gpcSegmentIncluded)
            this
        }

        Builder setGpc(Boolean gpc) {
            fieldValue(UsCaField.GPC, gpc)
            this
        }

        @Override
        GppConsent build() {
            return new UsCaV1Consent(section, fieldValues)
        }
    }
}
