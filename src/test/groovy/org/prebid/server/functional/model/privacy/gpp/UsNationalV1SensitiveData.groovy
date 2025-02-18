package org.prebid.server.functional.model.privacy.gpp

import org.prebid.server.functional.util.PBSUtils

class UsNationalV1SensitiveData {

    DataActivity racialEthnicOrigin
    DataActivity religiousBeliefs
    DataActivity healthInfo
    DataActivity orientation
    DataActivity citizenshipStatus
    DataActivity geneticId
    DataActivity biometricId
    DataActivity geolocation
    DataActivity idNumbers
    DataActivity accountInfo
    DataActivity unionMembership
    DataActivity communicationContents

    static UsNationalV1SensitiveData generateRandomSensitiveData() {
        new UsNationalV1SensitiveData().tap {
            racialEthnicOrigin = PBSUtils.getRandomEnum(DataActivity)
            religiousBeliefs = PBSUtils.getRandomEnum(DataActivity)
            healthInfo = PBSUtils.getRandomEnum(DataActivity)
            orientation = PBSUtils.getRandomEnum(DataActivity)
            citizenshipStatus = PBSUtils.getRandomEnum(DataActivity)
            geneticId = PBSUtils.getRandomEnum(DataActivity)
            biometricId = PBSUtils.getRandomEnum(DataActivity)
            geolocation = PBSUtils.getRandomEnum(DataActivity)
            idNumbers = PBSUtils.getRandomEnum(DataActivity)
            accountInfo = PBSUtils.getRandomEnum(DataActivity)
            unionMembership = PBSUtils.getRandomEnum(DataActivity)
            communicationContents = PBSUtils.getRandomEnum(DataActivity)
        }
    }

    static UsNationalV1SensitiveData fromList(List<DataActivity> data) {
        if (data.size() != 12) {
            throw new IllegalArgumentException("Invalid data size. Expected 12 values.")
        }
        new UsNationalV1SensitiveData().tap {
            racialEthnicOrigin = data[0]
            religiousBeliefs = data[1]
            healthInfo = data[2]
            orientation = data[3]
            citizenshipStatus = data[4]
            geneticId = data[5]
            biometricId = data[6]
            geolocation = data[7]
            idNumbers = data[8]
            accountInfo = data[9]
            unionMembership = data[10]
            communicationContents = data[11]
        }
    }

    List<Integer> getContentList() {
        [racialEthnicOrigin, religiousBeliefs, healthInfo, orientation,
         citizenshipStatus, geneticId, biometricId, geolocation,
         idNumbers, accountInfo, unionMembership, communicationContents]*.value.collect { it ?: 0 }
    }
}
