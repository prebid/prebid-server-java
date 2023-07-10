package org.prebid.server.functional.util.privacy.gpp

import com.iab.gpp.encoder.field.UspCtV1Field
import com.iab.gpp.encoder.section.EncodableSection
import com.iab.gpp.encoder.section.UspCtV1
import org.prebid.server.functional.util.privacy.gpp.data.UsConnecticutSensitiveData
import org.prebid.server.functional.util.privacy.gpp.data.UsNationalSensitiveData
import org.prebid.server.functional.util.privacy.gpp.data.UsVirginiaSensitiveData

class UspCtV1Consent extends UsConsent {

    private static final Section SECTION = Section.USP_CT_V1

    protected UspCtV1Consent(Section section, Map<String, Object> fieldValues) {
        super(section, fieldValues)
    }

    @Override
    protected String encodeSection() {
        gppModel.encodeSection(SECTION.name)
    }

    @Override
    UspNatV1Consent normaliseToNational() {
        def uspCtV1 = ((UspCtV1) this.gppModel.getSection(SECTION.name))

        new UspNatV1Consent.Builder()
                .setSensitiveDataProcessing(normaliseSensitiveData(uspCtV1))
                .setKnownChildSensitiveDataConsents(normalizeChildConsents(uspCtV1))
                .setSaleOptOutNotice(uspCtV1.saleOptOutNotice)
                .setTargetedAdvertisingOptOutNotice(uspCtV1.targetedAdvertisingOptOutNotice)
                .setTargetedAdvertisingOptOut(uspCtV1.targetedAdvertisingOptOut)
                .setSharingNotice(uspCtV1.sharingNotice)
                .setSaleOptOut(uspCtV1.saleOptOut)
                .setMspaCoveredTransaction(uspCtV1.mspaCoveredTransaction)
                .setMspaOptOutOptionMode(uspCtV1.mspaOptOutOptionMode)
                .setMspaServiceProviderMode(uspCtV1.mspaServiceProviderMode)
                .build() as UspNatV1Consent
    }

    @Override
    protected UsNationalSensitiveData normaliseSensitiveData(EncodableSection uspCtV1) {
        def virginiaSensitiveData = UsVirginiaSensitiveData.fromList(((UspCtV1)uspCtV1).sensitiveDataProcessing)

        new UsNationalSensitiveData().tap {
            racialEthnicOrigin = virginiaSensitiveData.racialEthnicOrigin
            religiousBeliefs = virginiaSensitiveData.religiousBeliefs
            healthInfo = virginiaSensitiveData.healthInfo
            orientation = virginiaSensitiveData.orientation
            citizenshipStatus = virginiaSensitiveData.citizenshipStatus
            geneticId = virginiaSensitiveData.geneticId
            biometricId = virginiaSensitiveData.biometricId
            geolocation = virginiaSensitiveData.geolocation
            idNumbers = 0
            accountInfo = 0
            unionMembership = 0
            communicationContents = 0
        }
    }

    @Override
    protected List<Integer> normalizeChildConsents(EncodableSection uspCtV1) {
        List<Integer> childDataConsents = ((UspCtV1)uspCtV1).knownChildSensitiveDataConsents
        // no consent (2) for under 13
        Integer childUnder13 = childDataConsents[1] != 0 ? 1 : 0
        Integer childFrom13to16 = 1

        if (childDataConsents[1] == 0 && childDataConsents[2] == 0) {
            // when child from13 to 16 is not applicable (0)
            childFrom13to16 = 0
        } else if (childDataConsents[1] == 2 && childDataConsents[2] == 2) {
            // when child from13 to 16 is consent (2)
            childFrom13to16 = 2
        }

        [childFrom13to16, childUnder13]
    }

    static class Builder extends GppConsent.Builder {

        Builder() {
            super(SECTION)
        }

        Builder setVersion(Integer version) {
            fieldValue(UspCtV1Field.VERSION, version)
            this
        }

        Builder setSharingNotice(Integer sharingNotice) {
            fieldValue(UspCtV1Field.SHARING_NOTICE, sharingNotice)
            this
        }

        Builder setSaleOptOutNotice(Integer saleOptOutNotice) {
            fieldValue(UspCtV1Field.SALE_OPT_OUT_NOTICE, saleOptOutNotice)
            this
        }

        Builder setTargetedAdvertisingOptOutNotice(Integer targetedAdvertisingOptOutNotice) {
            fieldValue(UspCtV1Field.TARGETED_ADVERTISING_OPT_OUT_NOTICE, targetedAdvertisingOptOutNotice)
            this
        }

        Builder setSaleOptOut(Integer saleOptOut) {
            fieldValue(UspCtV1Field.SALE_OPT_OUT, saleOptOut)
            this
        }

        Builder setTargetedAdvertisingOptOut(Integer targetedAdvertisingOptOut) {
            fieldValue(UspCtV1Field.TARGETED_ADVERTISING_OPT_OUT, targetedAdvertisingOptOut)
            this
        }

        Builder setSensitiveDataProcessing(UsConnecticutSensitiveData sensitiveDataProcessing) {
            fieldValue(UspCtV1Field.SENSITIVE_DATA_PROCESSING, sensitiveDataProcessing.contentList)
            this
        }

        Builder setKnownChildSensitiveDataConsents(Integer childFrom13to16, Integer childBlow13, Integer childFrom16to18) {
            fieldValue(UspCtV1Field.KNOWN_CHILD_SENSITIVE_DATA_CONSENTS, [childFrom13to16, childBlow13, childFrom16to18])
            this
        }

        Builder setMspaCoveredTransaction(Integer mspaCoveredTransaction) {
            fieldValue(UspCtV1Field.MSPA_COVERED_TRANSACTION, mspaCoveredTransaction)
            this
        }

        Builder setMspaOptOutOptionMode(Integer mspaOptOutOptionMode) {
            fieldValue(UspCtV1Field.MSPA_OPT_OUT_OPTION_MODE, mspaOptOutOptionMode)
            this
        }

        Builder setMspaServiceProviderMode(Integer mspaServiceProviderMode) {
            fieldValue(UspCtV1Field.MSPA_SERVICE_PROVIDER_MODE, mspaServiceProviderMode)
            this
        }

        Builder setGpcSegmentType(Integer gpcSegmentType) {
            fieldValue(UspCtV1Field.GPC_SEGMENT_TYPE, gpcSegmentType)
            this
        }

        Builder setGpcSegmentIncluded(Integer gpcSegmentIncluded) {
            fieldValue(UspCtV1Field.GPC_SEGMENT_INCLUDED, gpcSegmentIncluded)
            this
        }

        Builder setGpc(Boolean gpc) {
            fieldValue(UspCtV1Field.GPC, gpc)
            this
        }

        @Override
        GppConsent build() {
            return new UspCtV1Consent(section, fieldValues)
        }
    }
}
