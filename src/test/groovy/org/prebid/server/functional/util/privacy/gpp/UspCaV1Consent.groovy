package org.prebid.server.functional.util.privacy.gpp

import com.iab.gpp.encoder.field.UspCaV1Field
import com.iab.gpp.encoder.section.EncodableSection
import com.iab.gpp.encoder.section.UspCaV1
import org.prebid.server.functional.util.privacy.gpp.data.UsCaliforniaSensitiveData
import org.prebid.server.functional.util.privacy.gpp.data.UsNationalSensitiveData

class UspCaV1Consent extends UsConsent {

    private static final Section SECTION = Section.USP_CA_V1

    protected UspCaV1Consent(Section section, Map<String, Object> fieldValues) {
        super(section, fieldValues)
    }

    @Override
    protected String encodeSection() {
        gppModel.encodeSection(SECTION.name)
    }

    @Override
    UspNatV1Consent normaliseToNational() {
        def UspCaV1 = ((UspCaV1) this.gppModel.getSection(SECTION.name))

        new UspNatV1Consent.Builder()
                .setSensitiveDataProcessing(normaliseSensitiveData(UspCaV1))
                .setKnownChildSensitiveDataConsents(normalizeChildConsents(UspCaV1))
                .setSaleOptOutNotice(UspCaV1.saleOptOutNotice)
                .setSharingOptOutNotice(UspCaV1.sharingOptOutNotice)
                .setSensitiveDataLimitUseNotice(UspCaV1.sensitiveDataLimitUseNotice)
                .setSaleOptOut(UspCaV1.saleOptOut)
                .setSharingOptOut(UspCaV1.sharingOptOut)
                .setPersonalDataConsents(UspCaV1.personalDataConsents)
                .setMspaCoveredTransaction(UspCaV1.mspaCoveredTransaction)
                .setMspaOptOutOptionMode(UspCaV1.mspaOptOutOptionMode)
                .setMspaServiceProviderMode(UspCaV1.mspaServiceProviderMode)
//                .setGpcSegmentType(UspCaV1.gpcSegmentType)
                .setGpcSegmentIncluded(UspCaV1.gpcSegmentIncluded)
                .setGpc(UspCaV1.gpc)
                .build() as UspNatV1Consent
    }

    @Override
    protected UsNationalSensitiveData normaliseSensitiveData(EncodableSection uspCaV1) {
        def californiaSensitiveData = UsCaliforniaSensitiveData.fromList(((UspCaV1)uspCaV1).sensitiveDataProcessing)

        new UsNationalSensitiveData().tap {
            racialEthnicOrigin = californiaSensitiveData.racialEthnicOrigin
            healthInfo = californiaSensitiveData.healthInfo
            orientation = californiaSensitiveData.orientation
            geneticId = californiaSensitiveData.geneticId
            biometricId = californiaSensitiveData.biometricId
            geolocation = californiaSensitiveData.geolocation
            idNumbers = californiaSensitiveData.idNumbers
            accountInfo = californiaSensitiveData.accountInfo
            communicationContents = californiaSensitiveData.communicationContents
            //
            religiousBeliefs = 0
            citizenshipStatus = 0
            unionMembership = 0
        }
    }

    @Override
    protected List<Integer> normalizeChildConsents(EncodableSection uspCaV1) {
        def childConsents = ((UspCaV1)uspCaV1).knownChildSensitiveDataConsents
        switch (childConsents) {
            case [0, 0]:
                // No change needed
                return childConsents
            default:
                return [1, 1]
        }
    }

    static class Builder extends GppConsent.Builder {

        Builder() {
            super(SECTION)
        }

        Builder setVersion(Integer version) {
            fieldValue(UspCaV1Field.VERSION, version)
            this
        }

        Builder setSaleOptOutNotice(Integer saleOptOutNotice) {
            fieldValue(UspCaV1Field.SALE_OPT_OUT_NOTICE, saleOptOutNotice)
            this
        }

        Builder setSharingOptOutNotice(Integer sharingOptOutNotice) {
            fieldValue(UspCaV1Field.SHARING_OPT_OUT_NOTICE, sharingOptOutNotice)
            this
        }

        Builder setSensitiveDataLimitUseNotice(Integer sensitiveDataLimitUseNotice) {
            fieldValue(UspCaV1Field.SENSITIVE_DATA_LIMIT_USE_NOTICE, sensitiveDataLimitUseNotice)
            this
        }

        Builder setSaleOptOut(Integer saleOptOut) {
            fieldValue(UspCaV1Field.SALE_OPT_OUT, saleOptOut)
            this
        }

        Builder setSharingOptOut(Integer sharingOptOut) {
            fieldValue(UspCaV1Field.SHARING_OPT_OUT, sharingOptOut)
            this
        }

        Builder setSensitiveDataProcessing(UsCaliforniaSensitiveData sensitiveDataProcessing) {
            fieldValue(UspCaV1Field.SENSITIVE_DATA_PROCESSING, sensitiveDataProcessing.contentList)
            this
        }

        Builder setKnownChildSensitiveDataConsents(Integer childFrom13to16, Integer childBlow13) {
            fieldValue(UspCaV1Field.KNOWN_CHILD_SENSITIVE_DATA_CONSENTS, [childFrom13to16, childBlow13])
            this
        }

        Builder setPersonalDataConsents(Integer personalDataConsents) {
            fieldValue(UspCaV1Field.PERSONAL_DATA_CONSENTS, personalDataConsents)
            this
        }

        Builder setMspaCoveredTransaction(Integer mspaCoveredTransaction) {
            fieldValue(UspCaV1Field.MSPA_COVERED_TRANSACTION, mspaCoveredTransaction)
            this
        }

        Builder setMspaOptOutOptionMode(Integer mspaOptOutOptionMode) {
            fieldValue(UspCaV1Field.MSPA_OPT_OUT_OPTION_MODE, mspaOptOutOptionMode)
            this
        }

        Builder setMspaServiceProviderMode(Integer mspaServiceProviderMode) {
            fieldValue(UspCaV1Field.MSPA_SERVICE_PROVIDER_MODE, mspaServiceProviderMode)
            this
        }

        Builder setGpcSegmentType(Integer gpcSegmentType) {
            fieldValue(UspCaV1Field.GPC_SEGMENT_TYPE, gpcSegmentType)
            this
        }

        Builder setGpcSegmentIncluded(Boolean gpcSegmentIncluded) {
            fieldValue(UspCaV1Field.GPC_SEGMENT_INCLUDED, gpcSegmentIncluded)
            this
        }

        Builder setGpc(Boolean gpc) {
            fieldValue(UspCaV1Field.GPC, gpc)
            this
        }

        @Override
        GppConsent build() {
            return new UspCaV1Consent(section, fieldValues)
        }
    }
}
