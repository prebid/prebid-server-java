package org.prebid.server.functional.model.privacy.gpp

class UsCaliforniaV1SensitiveData {

    GppDataActivity idNumbers
    GppDataActivity accountInfo
    GppDataActivity geolocation
    GppDataActivity racialEthnicOrigin
    GppDataActivity communicationContents
    GppDataActivity geneticId
    GppDataActivity biometricId
    GppDataActivity healthInfo
    GppDataActivity orientation

    List<Integer> getContentList() {
        [idNumbers, accountInfo, geolocation, racialEthnicOrigin,
         communicationContents, geneticId, biometricId, healthInfo, orientation]*.value.collect { it ?: 0 }
    }
}
