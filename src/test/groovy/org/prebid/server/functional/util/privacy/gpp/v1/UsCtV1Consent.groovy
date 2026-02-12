package org.prebid.server.functional.util.privacy.gpp.v1

import com.iab.gpp.encoder.field.UsCtField
import org.prebid.server.functional.model.privacy.gpp.MspaMode
import org.prebid.server.functional.model.privacy.gpp.Notice
import org.prebid.server.functional.model.privacy.gpp.OptOut
import org.prebid.server.functional.model.privacy.gpp.UsConnecticutV1ChildSensitiveData
import org.prebid.server.functional.model.privacy.gpp.UsConnecticutV1SensitiveData
import org.prebid.server.functional.util.privacy.gpp.GppConsent

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

        Builder setSharingNotice(Notice sharingNotice) {
            fieldValue(UsCtField.SHARING_NOTICE, sharingNotice.value)
            this
        }

        Builder setSaleOptOutNotice(Notice saleOptOutNotice) {
            fieldValue(UsCtField.SALE_OPT_OUT_NOTICE, saleOptOutNotice.value)
            this
        }

        Builder setTargetedAdvertisingOptOutNotice(Notice targetedAdvertisingOptOutNotice) {
            fieldValue(UsCtField.TARGETED_ADVERTISING_OPT_OUT_NOTICE, targetedAdvertisingOptOutNotice.value)
            this
        }

        Builder setSaleOptOut(OptOut saleOptOut) {
            fieldValue(UsCtField.SALE_OPT_OUT, saleOptOut.value)
            this
        }

        Builder setTargetedAdvertisingOptOut(OptOut targetedAdvertisingOptOut) {
            fieldValue(UsCtField.TARGETED_ADVERTISING_OPT_OUT, targetedAdvertisingOptOut.value)
            this
        }

        Builder setSensitiveDataProcessing(UsConnecticutV1SensitiveData sensitiveDataProcessing) {
            fieldValue(UsCtV1Field.SENSITIVE_DATA_PROCESSING, sensitiveDataProcessing.contentList)
            this
        }

        Builder setKnownChildSensitiveDataConsents(UsConnecticutV1ChildSensitiveData sensitiveData) {
            fieldValue(UsCtField.KNOWN_CHILD_SENSITIVE_DATA_CONSENTS, sensitiveData.contentList)
            this
        }

        Builder setMspaCoveredTransaction(MspaMode mspaCoveredTransaction) {
            fieldValue(UsCtField.MSPA_COVERED_TRANSACTION, mspaCoveredTransaction.value)
            this
        }

        Builder setMspaOptOutOptionMode(MspaMode mspaOptOutOptionMode) {
            fieldValue(UsCtField.MSPA_OPT_OUT_OPTION_MODE, mspaOptOutOptionMode.value)
            this
        }

        Builder setMspaServiceProviderMode(MspaMode mspaServiceProviderMode) {
            fieldValue(UsCtField.MSPA_SERVICE_PROVIDER_MODE, mspaServiceProviderMode.value)
            this
        }

        Builder setGpcSegmentType(Integer gpcSegmentType) {
            fieldValue(UsCtField.GPC_SEGMENT_TYPE, gpcSegmentType)
            this
        }

        Builder setGpcSegmentIncluded(Boolean gpcSegmentIncluded) {
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
