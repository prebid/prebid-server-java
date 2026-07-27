package org.prebid.server.functional.model.privacy.gpp

class UsColoradoV1SensitiveData {

    GppDataActivity racialEthnicOrigin
    GppDataActivity religiousBeliefs
    GppDataActivity healthInfo
    GppDataActivity orientation
    GppDataActivity citizenshipStatus
    GppDataActivity geneticId
    GppDataActivity biometricId

    List<Integer> getContentList() {
        [racialEthnicOrigin, religiousBeliefs, healthInfo, orientation,
         citizenshipStatus, geneticId, biometricId]*.value.collect { it ?: 0 }
    }
}
