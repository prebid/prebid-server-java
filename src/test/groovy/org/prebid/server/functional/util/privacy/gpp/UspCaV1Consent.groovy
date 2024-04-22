package org.prebid.server.functional.util.privacy.gpp

import com.iab.gpp.encoder.field.UsCaV1Field
import org.prebid.server.functional.util.privacy.gpp.data.UsCaliforniaSensitiveData

class UsCaV1Consent extends GppConsent {

    private static final Section SECTION = Section.USP_CA_V1

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
            fieldValue(UsCaV1Field.VERSION, version)
            this
        }

        Builder setSaleOptOutNotice(Integer saleOptOutNotice) {
            fieldValue(UsCaV1Field.SALE_OPT_OUT_NOTICE, saleOptOutNotice)
            this
        }

        Builder setSharingOptOutNotice(Integer sharingOptOutNotice) {
            fieldValue(UsCaV1Field.SHARING_OPT_OUT_NOTICE, sharingOptOutNotice)
            this
        }

        Builder setSensitiveDataLimitUseNotice(Integer sensitiveDataLimitUseNotice) {
            fieldValue(UsCaV1Field.SENSITIVE_DATA_LIMIT_USE_NOTICE, sensitiveDataLimitUseNotice)
            this
        }

        Builder setSaleOptOut(Integer saleOptOut) {
            fieldValue(UsCaV1Field.SALE_OPT_OUT, saleOptOut)
            this
        }

        Builder setSharingOptOut(Integer sharingOptOut) {
            fieldValue(UsCaV1Field.SHARING_OPT_OUT, sharingOptOut)
            this
        }

        Builder setSensitiveDataProcessing(UsCaliforniaSensitiveData sensitiveDataProcessing) {
            fieldValue(UsCaV1Field.SENSITIVE_DATA_PROCESSING, sensitiveDataProcessing.contentList)
            this
        }

        Builder setKnownChildSensitiveDataConsents(Integer childFrom13to16, Integer childBlow13) {
            fieldValue(UsCaV1Field.KNOWN_CHILD_SENSITIVE_DATA_CONSENTS, [childFrom13to16, childBlow13])
            this
        }

        Builder setPersonalDataConsents(Integer personalDataConsents) {
            fieldValue(UsCaV1Field.PERSONAL_DATA_CONSENTS, personalDataConsents)
            this
        }

        Builder setMspaCoveredTransaction(Integer mspaCoveredTransaction) {
            fieldValue(UsCaV1Field.MSPA_COVERED_TRANSACTION, mspaCoveredTransaction)
            this
        }

        Builder setMspaOptOutOptionMode(Integer mspaOptOutOptionMode) {
            fieldValue(UsCaV1Field.MSPA_OPT_OUT_OPTION_MODE, mspaOptOutOptionMode)
            this
        }

        Builder setMspaServiceProviderMode(Integer mspaServiceProviderMode) {
            fieldValue(UsCaV1Field.MSPA_SERVICE_PROVIDER_MODE, mspaServiceProviderMode)
            this
        }

        Builder setGpcSegmentType(Integer gpcSegmentType) {
            fieldValue(UsCaV1Field.GPC_SEGMENT_TYPE, gpcSegmentType)
            this
        }

        Builder setGpcSegmentIncluded(Boolean gpcSegmentIncluded) {
            fieldValue(UsCaV1Field.GPC_SEGMENT_INCLUDED, gpcSegmentIncluded)
            this
        }

        Builder setGpc(Boolean gpc) {
            fieldValue(UsCaV1Field.GPC, gpc)
            this
        }

        @Override
        GppConsent build() {
            return new UsCaV1Consent(section, fieldValues)
        }
    }
}
