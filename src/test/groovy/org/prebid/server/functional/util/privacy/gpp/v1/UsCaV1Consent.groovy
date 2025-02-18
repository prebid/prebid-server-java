package org.prebid.server.functional.util.privacy.gpp.v1

import com.iab.gpp.encoder.field.UsCaV1Field
import org.prebid.server.functional.model.privacy.gpp.DataActivity
import org.prebid.server.functional.model.privacy.gpp.MspaMode
import org.prebid.server.functional.model.privacy.gpp.Notice
import org.prebid.server.functional.model.privacy.gpp.OptOut
import org.prebid.server.functional.model.privacy.gpp.UsCaliforniaV1ChildSensitiveData
import org.prebid.server.functional.util.privacy.gpp.GppConsent
import org.prebid.server.functional.model.privacy.gpp.UsCaliforniaV1SensitiveData

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

        Builder setSaleOptOutNotice(Notice saleOptOutNotice) {
            fieldValue(UsCaV1Field.SALE_OPT_OUT_NOTICE, saleOptOutNotice.value)
            this
        }

        Builder setSharingOptOutNotice(Notice sharingOptOutNotice) {
            fieldValue(UsCaV1Field.SHARING_OPT_OUT_NOTICE, sharingOptOutNotice.value)
            this
        }

        Builder setSensitiveDataLimitUseNotice(Notice sensitiveDataLimitUseNotice) {
            fieldValue(UsCaV1Field.SENSITIVE_DATA_LIMIT_USE_NOTICE, sensitiveDataLimitUseNotice.value)
            this
        }

        Builder setSaleOptOut(OptOut saleOptOut) {
            fieldValue(UsCaV1Field.SALE_OPT_OUT, saleOptOut.value)
            this
        }

        Builder setSharingOptOut(OptOut sharingOptOut) {
            fieldValue(UsCaV1Field.SHARING_OPT_OUT, sharingOptOut.value)
            this
        }

        Builder setSensitiveDataProcessing(UsCaliforniaV1SensitiveData sensitiveDataProcessing) {
            fieldValue(UsCaV1Field.SENSITIVE_DATA_PROCESSING, sensitiveDataProcessing.contentList)
            this
        }

        Builder setKnownChildSensitiveDataConsents(UsCaliforniaV1ChildSensitiveData sensitiveData) {
            fieldValue(UsCaV1Field.KNOWN_CHILD_SENSITIVE_DATA_CONSENTS, sensitiveData.contentList)
            this
        }

        Builder setPersonalDataConsents(DataActivity personalDataConsents) {
            fieldValue(UsCaV1Field.PERSONAL_DATA_CONSENTS, personalDataConsents.value)
            this
        }

        Builder setMspaCoveredTransaction(Boolean mspaCoveredTransaction) {
            fieldValue(UsCaV1Field.MSPA_COVERED_TRANSACTION, mspaCoveredTransaction)
            this
        }

        Builder setMspaOptOutOptionMode(MspaMode mspaOptOutOptionMode) {
            fieldValue(UsCaV1Field.MSPA_OPT_OUT_OPTION_MODE, mspaOptOutOptionMode.value)
            this
        }

        Builder setMspaServiceProviderMode(MspaMode mspaServiceProviderMode) {
            fieldValue(UsCaV1Field.MSPA_SERVICE_PROVIDER_MODE, mspaServiceProviderMode.value)
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
