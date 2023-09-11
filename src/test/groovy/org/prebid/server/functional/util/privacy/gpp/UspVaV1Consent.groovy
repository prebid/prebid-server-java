package org.prebid.server.functional.util.privacy.gpp

import com.iab.gpp.encoder.field.UspVaV1Field
import com.iab.gpp.encoder.section.EncodableSection
import com.iab.gpp.encoder.section.UspVaV1
import org.prebid.server.functional.util.privacy.gpp.data.UsNationalSensitiveData
import org.prebid.server.functional.util.privacy.gpp.data.UsVirginiaSensitiveData

class UspVaV1Consent extends UsConsent {

    private static final Section SECTION = Section.USP_VA_V1

    protected UspVaV1Consent(Section section, Map<String, Object> fieldValues) {
        super(section, fieldValues)
    }

    @Override
    protected String encodeSection() {
        gppModel.encodeSection(SECTION.name)
    }

    @Override
    UspNatV1Consent normaliseToNational() {
        def uspVaV1 = ((UspVaV1) this.gppModel.getSection(SECTION.name))

        new UspNatV1Consent.Builder()
                .setSensitiveDataProcessing(normaliseSensitiveData(uspVaV1))
                .setKnownChildSensitiveDataConsents(normalizeChildConsents(uspVaV1))
                .setSaleOptOutNotice(uspVaV1.saleOptOutNotice)
                .setTargetedAdvertisingOptOutNotice(uspVaV1.targetedAdvertisingOptOutNotice)
                .setTargetedAdvertisingOptOut(uspVaV1.targetedAdvertisingOptOut)
                .setSharingNotice(uspVaV1.sharingNotice)
                .setSaleOptOut(uspVaV1.saleOptOut)
                .setMspaCoveredTransaction(uspVaV1.mspaCoveredTransaction)
                .setMspaOptOutOptionMode(uspVaV1.mspaOptOutOptionMode)
                .setMspaServiceProviderMode(uspVaV1.mspaServiceProviderMode)
                .build() as UspNatV1Consent
    }

    @Override
    protected UsNationalSensitiveData normaliseSensitiveData(EncodableSection uspVaV1) {
        def virginiaSensitiveData = UsVirginiaSensitiveData.fromList(((UspVaV1)uspVaV1).sensitiveDataProcessing)

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
    protected List<Integer> normalizeChildConsents(EncodableSection uspVaV1) {
        (((UspVaV1)uspVaV1).knownChildSensitiveDataConsents != 0) ? [1, 1] : [0, 0]
    }

    static class Builder extends GppConsent.Builder {

        Builder() {
            super(SECTION)
        }

        Builder setVersion(Integer version) {
            fieldValue(UspVaV1Field.VERSION, version)
            this
        }

        Builder setSharingNotice(Integer sharingNotice) {
            fieldValue(UspVaV1Field.SHARING_NOTICE, sharingNotice)
            this
        }

        Builder setSaleOptOutNotice(Integer saleOptOutNotice) {
            fieldValue(UspVaV1Field.SALE_OPT_OUT_NOTICE, saleOptOutNotice)
            this
        }

        Builder setTargetedAdvertisingOptOutNotice(Integer targetedAdvertisingOptOutNotice) {
            fieldValue(UspVaV1Field.TARGETED_ADVERTISING_OPT_OUT_NOTICE, targetedAdvertisingOptOutNotice)
            this
        }

        Builder setSaleOptOut(Integer saleOptOut) {
            fieldValue(UspVaV1Field.SALE_OPT_OUT, saleOptOut)
            this
        }

        Builder setTargetedAdvertisingOptOut(Integer targetedAdvertisingOptOut) {
            fieldValue(UspVaV1Field.TARGETED_ADVERTISING_OPT_OUT, targetedAdvertisingOptOut)
            this
        }

        Builder setSensitiveDataProcessing(UsVirginiaSensitiveData sensitiveDataProcessing) {
            fieldValue(UspVaV1Field.SENSITIVE_DATA_PROCESSING, sensitiveDataProcessing.contentList)
            this
        }

        Builder setKnownChildSensitiveDataConsents(Integer childSensitiveDataConsents) {
            fieldValue(UspVaV1Field.KNOWN_CHILD_SENSITIVE_DATA_CONSENTS, childSensitiveDataConsents)
            this
        }

        Builder setMspaCoveredTransaction(Integer mspaCoveredTransaction) {
            fieldValue(UspVaV1Field.MSPA_COVERED_TRANSACTION, mspaCoveredTransaction)
            this
        }

        Builder setMspaOptOutOptionMode(Integer mspaOptOutOptionMode) {
            fieldValue(UspVaV1Field.MSPA_OPT_OUT_OPTION_MODE, mspaOptOutOptionMode)
            this
        }

        Builder setMspaServiceProviderMode(Integer mspaServiceProviderMode) {
            fieldValue(UspVaV1Field.MSPA_SERVICE_PROVIDER_MODE, mspaServiceProviderMode)
            this
        }

        @Override
        GppConsent build() {
            return new UspVaV1Consent(section, fieldValues)
        }
    }
}
