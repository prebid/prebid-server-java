package org.prebid.server.functional.model.privacy.gpp

class UsVirginiaV1SensitiveData {

    GppDataActivity racialEthnicOrigin
    GppDataActivity religiousBeliefs
    GppDataActivity healthInfo
    GppDataActivity orientation
    GppDataActivity citizenshipStatus
    GppDataActivity geneticId
    GppDataActivity biometricId
    GppDataActivity geolocation

    List<Integer> getContentList() {
        [racialEthnicOrigin, religiousBeliefs, healthInfo, orientation,
         citizenshipStatus, geneticId, biometricId, geolocation]*.value.collect { it ?: 0 }
    }
}
