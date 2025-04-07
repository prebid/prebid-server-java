package org.prebid.server.functional.util.privacy.gpp

import com.iab.gpp.encoder.field.UsCtField
import org.prebid.server.functional.util.privacy.gpp.data.UsConnecticutSensitiveData

class UsCtV1Consent extends GppConsent {

    private static final Section SECTION = Section.US_CT_V1

    protected UsCtV1Consent(Section section, Map<String, Object> fieldValues) {
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
            fieldValue(UsCtField.VERSION, version)
            this
        }

        Builder setSharingNotice(Integer sharingNotice) {
            fieldValue(UsCtField.SHARING_NOTICE, sharingNotice)
            this
        }

        Builder setSaleOptOutNotice(Integer saleOptOutNotice) {
            fieldValue(UsCtField.SALE_OPT_OUT_NOTICE, saleOptOutNotice)
            this
        }

        Builder setTargetedAdvertisingOptOutNotice(Integer targetedAdvertisingOptOutNotice) {
            fieldValue(UsCtField.TARGETED_ADVERTISING_OPT_OUT_NOTICE, targetedAdvertisingOptOutNotice)
            this
        }

        Builder setSaleOptOut(Integer saleOptOut) {
            fieldValue(UsCtField.SALE_OPT_OUT, saleOptOut)
            this
        }

        Builder setTargetedAdvertisingOptOut(Integer targetedAdvertisingOptOut) {
            fieldValue(UsCtField.TARGETED_ADVERTISING_OPT_OUT, targetedAdvertisingOptOut)
            this
        }

        Builder setSensitiveDataProcessing(UsConnecticutSensitiveData sensitiveDataProcessing) {
            fieldValue(UsCtField.SENSITIVE_DATA_PROCESSING, sensitiveDataProcessing.contentList)
            this
        }

        Builder setKnownChildSensitiveDataConsents(Integer childFrom13to16, Integer childBlow13, Integer childFrom16to18) {
            fieldValue(UsCtField.KNOWN_CHILD_SENSITIVE_DATA_CONSENTS, [childFrom13to16, childBlow13, childFrom16to18])
            this
        }

        Builder setMspaCoveredTransaction(Integer mspaCoveredTransaction) {
            fieldValue(UsCtField.MSPA_COVERED_TRANSACTION, mspaCoveredTransaction)
            this
        }

        Builder setMspaOptOutOptionMode(Integer mspaOptOutOptionMode) {
            fieldValue(UsCtField.MSPA_OPT_OUT_OPTION_MODE, mspaOptOutOptionMode)
            this
        }

        Builder setMspaServiceProviderMode(Integer mspaServiceProviderMode) {
            fieldValue(UsCtField.MSPA_SERVICE_PROVIDER_MODE, mspaServiceProviderMode)
            this
        }

        Builder setGpcSegmentType(Integer gpcSegmentType) {
            fieldValue(UsCtField.GPC_SEGMENT_TYPE, gpcSegmentType)
            this
        }

        Builder setGpcSegmentIncluded(Integer gpcSegmentIncluded) {
            fieldValue(UsCtField.GPC_SEGMENT_INCLUDED, gpcSegmentIncluded)
            this
        }

        Builder setGpc(Boolean gpc) {
            fieldValue(UsCtField.GPC, gpc)
            this
        }

        @Override
        GppConsent build() {
            return new UsCtV1Consent(section, fieldValues)
        }
    }
}
