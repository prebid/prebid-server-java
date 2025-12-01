package org.prebid.server.functional.model.privacy.gpp

class UsConnecticutV1SensitiveData {

    GppDataActivity racialEthnicOrigin
    GppDataActivity religiousBeliefs
    GppDataActivity healthInfo
    GppDataActivity orientation
    GppDataActivity citizenshipStatus
    GppDataActivity geneticId
    GppDataActivity biometricId
    GppDataActivity geolocation
    GppDataActivity idNumbers

    List<Integer> getContentList() {
        [racialEthnicOrigin, religiousBeliefs, healthInfo, orientation,
         citizenshipStatus, geneticId, biometricId, geolocation, idNumbers]*.value.collect { it ?: 0 }
    }
}
