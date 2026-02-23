package org.prebid.server.functional.model.privacy.gpp

class UsUtahV1SensitiveData {

    GppDataActivity racialEthnicOrigin
    GppDataActivity religiousBeliefs
    GppDataActivity orientation
    GppDataActivity citizenshipStatus
    GppDataActivity healthInfo
    GppDataActivity geneticId
    GppDataActivity biometricId
    GppDataActivity geolocation

    List<Integer> getContentList() {
        [racialEthnicOrigin, religiousBeliefs, orientation, citizenshipStatus,
         healthInfo, geneticId, biometricId, geolocation]*.value.collect { it ?: 0 }
    }
}
