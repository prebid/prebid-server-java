package org.prebid.server.functional.model.privacy.gpp

import org.prebid.server.functional.util.PBSUtils

class UsConnecticutV1SensitiveData {

    DataActivity racialEthnicOrigin
    DataActivity religiousBeliefs
    DataActivity healthInfo
    DataActivity orientation
    DataActivity citizenshipStatus
    DataActivity geneticId
    DataActivity biometricId
    DataActivity geolocation
    DataActivity idNumbers

    static UsConnecticutV1SensitiveData generateRandomSensitiveData() {
        new UsConnecticutV1SensitiveData().tap {
            racialEthnicOrigin = PBSUtils.getRandomEnum(DataActivity)
            religiousBeliefs = PBSUtils.getRandomEnum(DataActivity)
            healthInfo = PBSUtils.getRandomEnum(DataActivity)
            orientation = PBSUtils.getRandomEnum(DataActivity)
            citizenshipStatus = PBSUtils.getRandomEnum(DataActivity)
            geneticId = PBSUtils.getRandomEnum(DataActivity)
            biometricId = PBSUtils.getRandomEnum(DataActivity)
            geolocation = PBSUtils.getRandomEnum(DataActivity)
            idNumbers = PBSUtils.getRandomEnum(DataActivity)
        }
    }

    static UsConnecticutV1SensitiveData fromList(List<DataActivity> data) {
        if (data.size() != 9) {
            throw new IllegalArgumentException("Invalid data size. Expected 9 values.")
        }
        new UsConnecticutV1SensitiveData().tap {
            racialEthnicOrigin = data[0]
            religiousBeliefs = data[1]
            healthInfo = data[2]
            orientation = data[3]
            citizenshipStatus = data[4]
            geneticId = data[5]
            biometricId = data[6]
            geolocation = data[7]
            idNumbers = data[8]
        }
    }

    List<Integer> getContentList() {
        [racialEthnicOrigin, religiousBeliefs, healthInfo, orientation,
         citizenshipStatus, geneticId, biometricId, geolocation, idNumbers]*.value.collect { it ?: 0 }
    }
}
