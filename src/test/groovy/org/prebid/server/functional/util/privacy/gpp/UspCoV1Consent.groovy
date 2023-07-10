package org.prebid.server.functional.util.privacy.gpp

import com.iab.gpp.encoder.field.UspCoV1Field
import com.iab.gpp.encoder.section.EncodableSection
import com.iab.gpp.encoder.section.UspCoV1
import org.prebid.server.functional.util.privacy.gpp.data.UsColoradoSensitiveData
import org.prebid.server.functional.util.privacy.gpp.data.UsNationalSensitiveData
import org.prebid.server.functional.util.privacy.gpp.data.UsVirginiaSensitiveData

class UspCoV1Consent extends UsConsent {

    private static final Section SECTION = Section.USP_CO_V1

    protected UspCoV1Consent(Section section, Map<String, Object> fieldValues) {
        super(section, fieldValues)
    }

    @Override
    protected String encodeSection() {
        gppModel.encodeSection(Section.USP_CO_V1.name)
    }

    @Override
    UspNatV1Consent normaliseToNational() {
        def uspCoV1 = ((UspCoV1) this.gppModel.getSection(SECTION.name))

        new UspNatV1Consent.Builder()
                .setSensitiveDataProcessing(normaliseSensitiveData(uspCoV1))
                .setKnownChildSensitiveDataConsents(normalizeChildConsents(uspCoV1))
                .setSaleOptOutNotice(uspCoV1.saleOptOutNotice)
                .setTargetedAdvertisingOptOutNotice(uspCoV1.targetedAdvertisingOptOutNotice)
                .setTargetedAdvertisingOptOut(uspCoV1.targetedAdvertisingOptOut)
                .setSharingNotice(uspCoV1.sharingNotice)
                .setSaleOptOut(uspCoV1.saleOptOut)
                .setMspaCoveredTransaction(uspCoV1.mspaCoveredTransaction)
                .setMspaOptOutOptionMode(uspCoV1.mspaOptOutOptionMode)
                .setMspaServiceProviderMode(uspCoV1.mspaServiceProviderMode)
//                .setGpcSegmentType(uspCoV1.gpcSegmentType)
                .setGpcSegmentIncluded(uspCoV1.gpcSegmentIncluded)
                .setGpc(uspCoV1.gpc)
                .build() as UspNatV1Consent
    }

    @Override
    protected UsNationalSensitiveData normaliseSensitiveData(EncodableSection uspCoV1) {
        def coloradoSensitiveData = UsColoradoSensitiveData.fromList(((UspCoV1)uspCoV1).sensitiveDataProcessing)

        new UsNationalSensitiveData().tap {
            racialEthnicOrigin = coloradoSensitiveData.racialEthnicOrigin
            religiousBeliefs = coloradoSensitiveData.religiousBeliefs
            healthInfo = coloradoSensitiveData.healthInfo
            orientation = coloradoSensitiveData.orientation
            citizenshipStatus = coloradoSensitiveData.citizenshipStatus
            geneticId = coloradoSensitiveData.geneticId
            biometricId = coloradoSensitiveData.biometricId
            geolocation = 0
            idNumbers = 0
            accountInfo = 0
            unionMembership = 0
            communicationContents = 0
        }
    }

    @Override
    protected List<Integer> normalizeChildConsents(EncodableSection uspCoV1) {
        // value 1 or 2 then n/a for 13-16 and no consent for under age 13
        (((UspCoV1)uspCoV1).knownChildSensitiveDataConsents != 0) ? [0, 1] : [0, 0]
    }

    static class Builder extends GppConsent.Builder {

        Builder() {
            super(SECTION)
        }

        Builder setVersion(Integer version) {
            fieldValue(UspCoV1Field.VERSION, version)
            this
        }

        Builder setSharingNotice(Integer sharingNotice) {
            fieldValue(UspCoV1Field.SHARING_NOTICE, sharingNotice)
            this
        }

        Builder setSaleOptOutNotice(Integer saleOptOutNotice) {
            fieldValue(UspCoV1Field.SALE_OPT_OUT_NOTICE, saleOptOutNotice)
            this
        }

        Builder setTargetedAdvertisingOptOutNotice(Integer targetedAdvertisingOptOutNotice) {
            fieldValue(UspCoV1Field.TARGETED_ADVERTISING_OPT_OUT_NOTICE, targetedAdvertisingOptOutNotice)
            this
        }

        Builder setSaleOptOut(Integer saleOptOut) {
            fieldValue(UspCoV1Field.SALE_OPT_OUT, saleOptOut)
            this
        }

        Builder setTargetedAdvertisingOptOut(Integer targetedAdvertisingOptOut) {
            fieldValue(UspCoV1Field.TARGETED_ADVERTISING_OPT_OUT, targetedAdvertisingOptOut)
            this
        }

        Builder setSensitiveDataProcessing(UsColoradoSensitiveData sensitiveDataProcessing) {
            fieldValue(UspCoV1Field.SENSITIVE_DATA_PROCESSING, sensitiveDataProcessing.contentList)
            this
        }

        Builder setKnownChildSensitiveDataConsents(Integer knownChildSensitiveDataConsents) {
            fieldValue(UspCoV1Field.KNOWN_CHILD_SENSITIVE_DATA_CONSENTS, knownChildSensitiveDataConsents)
            this
        }

        Builder setMspaCoveredTransaction(Integer mspaCoveredTransaction) {
            fieldValue(UspCoV1Field.MSPA_COVERED_TRANSACTION, mspaCoveredTransaction)
            this
        }

        Builder setMspaOptOutOptionMode(Integer mspaOptOutOptionMode) {
            fieldValue(UspCoV1Field.MSPA_OPT_OUT_OPTION_MODE, mspaOptOutOptionMode)
            this
        }

        Builder setMspaServiceProviderMode(Integer mspaServiceProviderMode) {
            fieldValue(UspCoV1Field.MSPA_SERVICE_PROVIDER_MODE, mspaServiceProviderMode)
            this
        }

        Builder setGpcSegmentType(Integer gpcSegmentType) {
            fieldValue(UspCoV1Field.GPC_SEGMENT_TYPE, gpcSegmentType)
            this
        }

        Builder setGpcSegmentIncluded(Integer gpcSegmentIncluded) {
            fieldValue(UspCoV1Field.GPC_SEGMENT_INCLUDED, gpcSegmentIncluded)
            this
        }

        Builder setGpc(Boolean gpc) {
            fieldValue(UspCoV1Field.GPC, gpc)
            this
        }

        @Override
        GppConsent build() {
            new UspCoV1Consent(section, fieldValues)
        }
    }
}
