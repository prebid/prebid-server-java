package org.prebid.server.functional.model.privacy.gpp

class UsNationalV1SensitiveData {

    GppDataActivity racialEthnicOrigin
    GppDataActivity religiousBeliefs
    GppDataActivity healthInfo
    GppDataActivity orientation
    GppDataActivity citizenshipStatus
    GppDataActivity geneticId
    GppDataActivity biometricId
    GppDataActivity geolocation
    GppDataActivity idNumbers
    GppDataActivity accountInfo
    GppDataActivity unionMembership
    GppDataActivity communicationContents

    List<Integer> getContentList() {
        [racialEthnicOrigin, religiousBeliefs, healthInfo, orientation,
         citizenshipStatus, geneticId, biometricId, geolocation,
         idNumbers, accountInfo, unionMembership, communicationContents]*.value.collect { it ?: 0 }
    }
}
