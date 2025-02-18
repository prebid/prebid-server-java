package org.prebid.server.functional.model.privacy.gpp

import org.prebid.server.functional.util.PBSUtils

class UsUtahV1SensitiveData {

    DataActivity racialEthnicOrigin
    DataActivity religiousBeliefs
    DataActivity orientation
    DataActivity citizenshipStatus
    DataActivity healthInfo
    DataActivity geneticId
    DataActivity biometricId
    DataActivity geolocation

    static UsUtahV1SensitiveData generateRandomSensitiveData() {
        new UsUtahV1SensitiveData().tap {
            racialEthnicOrigin = PBSUtils.getRandomEnum(DataActivity)
            religiousBeliefs = PBSUtils.getRandomEnum(DataActivity)
            orientation = PBSUtils.getRandomEnum(DataActivity)
            citizenshipStatus = PBSUtils.getRandomEnum(DataActivity)
            healthInfo = PBSUtils.getRandomEnum(DataActivity)
            geneticId = PBSUtils.getRandomEnum(DataActivity)
            biometricId = PBSUtils.getRandomEnum(DataActivity)
            geolocation = PBSUtils.getRandomEnum(DataActivity)
        }
    }

    static UsUtahV1SensitiveData fromList(List<DataActivity> data) {
        if (data.size() != 8) {
            throw new IllegalArgumentException("Invalid data size. Expected 8 values.")
        }
        new UsUtahV1SensitiveData().tap {
            racialEthnicOrigin = data[0]
            religiousBeliefs = data[1]
            orientation = data[2]
            citizenshipStatus = data[3]
            healthInfo = data[4]
            geneticId = data[5]
            biometricId = data[6]
            geolocation = data[7]
        }
    }

    List<Integer> getContentList() {
        [racialEthnicOrigin, religiousBeliefs, orientation, citizenshipStatus,
         healthInfo, geneticId, biometricId, geolocation]*.value.collect { it ?: 0 }
    }
}
