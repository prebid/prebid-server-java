package org.prebid.server.functional.util.privacy.gpp.v1

import com.iab.gpp.encoder.field.UsCoV1Field
import org.prebid.server.functional.model.privacy.gpp.MspaMode
import org.prebid.server.functional.model.privacy.gpp.Notice
import org.prebid.server.functional.model.privacy.gpp.OptOut
import org.prebid.server.functional.model.privacy.gpp.UsColoradoV1ChildSensitiveData
import org.prebid.server.functional.util.privacy.gpp.GppConsent
import org.prebid.server.functional.model.privacy.gpp.UsColoradoV1SensitiveData

class UsCoV1Consent extends GppConsent {

    private static final Section SECTION = Section.US_CO_V1

    protected UsCoV1Consent(Section section, Map<String, Object> fieldValues) {
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
            fieldValue(UsCoV1Field.SHARING_NOTICE, sharingNotice.value)
            this
        }

        Builder setSaleOptOutNotice(Notice saleOptOutNotice) {
            fieldValue(UsCoV1Field.SALE_OPT_OUT_NOTICE, saleOptOutNotice.value)
            this
        }

        Builder setTargetedAdvertisingOptOutNotice(Notice targetedAdvertisingOptOutNotice) {
            fieldValue(UsCoV1Field.TARGETED_ADVERTISING_OPT_OUT_NOTICE, targetedAdvertisingOptOutNotice.value)
            this
        }

        Builder setSaleOptOut(OptOut saleOptOut) {
            fieldValue(UsCoV1Field.SALE_OPT_OUT, saleOptOut.value)
            this
        }

        Builder setTargetedAdvertisingOptOut(OptOut targetedAdvertisingOptOut) {
            fieldValue(UsCoV1Field.TARGETED_ADVERTISING_OPT_OUT, targetedAdvertisingOptOut.value)
            this
        }

        Builder setSensitiveDataProcessing(UsColoradoV1SensitiveData sensitiveDataProcessing) {
            fieldValue(UsCoV1Field.SENSITIVE_DATA_PROCESSING, sensitiveDataProcessing.contentList)
            this
        }

        Builder setKnownChildSensitiveDataConsents(UsColoradoV1ChildSensitiveData sensitiveData) {
            fieldValue(UsCoV1Field.KNOWN_CHILD_SENSITIVE_DATA_CONSENTS, sensitiveData.contentList)
            this
        }

        Builder setMspaCoveredTransaction(MspaMode mspaCoveredTransaction) {
            fieldValue(UsCoV1Field.MSPA_COVERED_TRANSACTION, mspaCoveredTransaction.value)
            this
        }

        Builder setMspaOptOutOptionMode(MspaMode mspaOptOutOptionMode) {
            fieldValue(UsCoV1Field.MSPA_OPT_OUT_OPTION_MODE, mspaOptOutOptionMode.value)
            this
        }

        Builder setMspaServiceProviderMode(MspaMode mspaServiceProviderMode) {
            fieldValue(UsCoV1Field.MSPA_SERVICE_PROVIDER_MODE, mspaServiceProviderMode.value)
            this
        }

        Builder setGpcSegmentType(Integer gpcSegmentType) {
            fieldValue(UsCoV1Field.GPC_SEGMENT_TYPE, gpcSegmentType)
            this
        }

        Builder setGpcSegmentIncluded(Boolean gpcSegmentIncluded) {
            fieldValue(UsCoV1Field.GPC_SEGMENT_INCLUDED, gpcSegmentIncluded)
            this
        }

        Builder setGpc(Boolean gpc) {
            fieldValue(UsCoV1Field.GPC, gpc)
            this
        }

        @Override
        GppConsent build() {
            new UsCoV1Consent(section, fieldValues)
        }
    }
}
