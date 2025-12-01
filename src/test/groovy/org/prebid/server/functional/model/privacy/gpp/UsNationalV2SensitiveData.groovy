package org.prebid.server.functional.model.privacy.gpp

class UsNationalV2SensitiveData extends UsNationalV1SensitiveData {

    GppDataActivity consumerHealthData
    GppDataActivity crimeVictim
    GppDataActivity nationalOrigin
    GppDataActivity transgenderStatus

    @Override
    List<Integer> getContentList() {
        [racialEthnicOrigin, religiousBeliefs, healthInfo, orientation,
         citizenshipStatus, geneticId, biometricId, geolocation,
         idNumbers, accountInfo, unionMembership, communicationContents,
         consumerHealthData, crimeVictim, nationalOrigin, transgenderStatus]*.value.collect { it ?: 0 }
    }
}
