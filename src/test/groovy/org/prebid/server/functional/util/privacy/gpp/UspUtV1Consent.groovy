package org.prebid.server.functional.util.privacy.gpp

import com.iab.gpp.encoder.field.UspUtV1Field
import com.iab.gpp.encoder.section.EncodableSection
import com.iab.gpp.encoder.section.UspUtV1
import org.prebid.server.functional.util.privacy.gpp.data.UsNationalSensitiveData
import org.prebid.server.functional.util.privacy.gpp.data.UsUtahSensitiveData
import org.prebid.server.functional.util.privacy.gpp.data.UsVirginiaSensitiveData

class UspUtV1Consent extends UsConsent {

    private static final Section SECTION = Section.USP_UT_V1

    protected UspUtV1Consent(Section section, Map<String, Object> fieldValues) {
        super(section, fieldValues)
    }

    @Override
    protected String encodeSection() {
        gppModel.encodeSection(SECTION.name)
    }

    @Override
    UspNatV1Consent normaliseToNational() {
        def uspVaV1 = ((UspUtV1) this.gppModel.getSection(SECTION.name))

        new UspNatV1Consent.Builder()
                .setSensitiveDataProcessing(normaliseSensitiveData(uspVaV1))
                .setKnownChildSensitiveDataConsents(normalizeChildConsents(uspVaV1))
                .setSaleOptOut(uspVaV1.saleOptOut)
                .setTargetedAdvertisingOptOut(uspVaV1.targetedAdvertisingOptOut)
                .setMspaCoveredTransaction(uspVaV1.mspaCoveredTransaction)
                .setMspaOptOutOptionMode(uspVaV1.mspaOptOutOptionMode)
                .setMspaServiceProviderMode(uspVaV1.mspaServiceProviderMode)
                .setSharingNotice(uspVaV1.sharingNotice)
                .build() as UspNatV1Consent
    }

    @Override
    protected UsNationalSensitiveData normaliseSensitiveData(EncodableSection uspUtV1) {
        def virginiaSensitiveData = UsVirginiaSensitiveData.fromList(((UspUtV1)uspUtV1).sensitiveDataProcessing)

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
    protected List<Integer> normalizeChildConsents(EncodableSection uspUtV1) {
        (((UspUtV1)uspUtV1).knownChildSensitiveDataConsents != 0) ? [1, 1] : [0, 0]
    }

    static class Builder extends GppConsent.Builder {

        Builder() {
            super(GppConsent.Section.USP_UT_V1)
        }

        Builder setVersion(Integer version) {
            fieldValue(UspUtV1Field.VERSION, version)
            this
        }

        Builder setSharingNotice(Integer sharingNotice) {
            fieldValue(UspUtV1Field.SHARING_NOTICE, sharingNotice)
            this
        }

        Builder setSaleOptOutNotice(Integer saleOptOutNotice) {
            fieldValue(UspUtV1Field.SALE_OPT_OUT_NOTICE, saleOptOutNotice)
            this
        }

        Builder setTargetedAdvertisingOptOutNotice(Integer targetedAdvertisingOptOutNotice) {
            fieldValue(UspUtV1Field.TARGETED_ADVERTISING_OPT_OUT_NOTICE, targetedAdvertisingOptOutNotice)
            this
        }

        Builder setSensitiveDataProcessingOptOutNotice(Integer sensitiveDataProcessingOptOutNotice) {
            fieldValue(UspUtV1Field.SENSITIVE_DATA_PROCESSING_OPT_OUT_NOTICE, sensitiveDataProcessingOptOutNotice)
            this
        }

        Builder setSaleOptOut(Integer saleOptOut) {
            fieldValue(UspUtV1Field.SALE_OPT_OUT, saleOptOut)
            this
        }

        Builder setTargetedAdvertisingOptOut(Integer targetedAdvertisingOptOut) {
            fieldValue(UspUtV1Field.TARGETED_ADVERTISING_OPT_OUT, targetedAdvertisingOptOut)
            this
        }

        Builder setSensitiveDataProcessing(UsUtahSensitiveData sensitiveDataProcessing) {
            fieldValue(UspUtV1Field.SENSITIVE_DATA_PROCESSING, sensitiveDataProcessing.contentList)
            this
        }

        Builder setKnownChildSensitiveDataConsents(Integer knownChildSensitiveDataConsents) {
            fieldValue(UspUtV1Field.KNOWN_CHILD_SENSITIVE_DATA_CONSENTS, knownChildSensitiveDataConsents)
            this
        }

        Builder setMspaCoveredTransaction(Integer mspaCoveredTransaction) {
            fieldValue(UspUtV1Field.MSPA_COVERED_TRANSACTION, mspaCoveredTransaction)
            this
        }

        Builder setMspaOptOutOptionMode(Integer mspaOptOutOptionMode) {
            fieldValue(UspUtV1Field.MSPA_OPT_OUT_OPTION_MODE, mspaOptOutOptionMode)
            this
        }

        Builder setMspaServiceProviderMode(Integer mspaServiceProviderMode) {
            fieldValue(UspUtV1Field.MSPA_SERVICE_PROVIDER_MODE, mspaServiceProviderMode)
            this
        }

        @Override
        GppConsent build() {
            return new UspUtV1Consent(section, fieldValues)
        }
    }
}
