package org.prebid.server.functional.model.privacy.gpp

import org.prebid.server.functional.util.PBSUtils

class UsColoradoV1SensitiveData {

    DataActivity racialEthnicOrigin
    DataActivity religiousBeliefs
    DataActivity healthInfo
    DataActivity orientation
    DataActivity citizenshipStatus
    DataActivity geneticId
    DataActivity biometricId

    static UsColoradoV1SensitiveData generateRandomSensitiveData() {
        new UsColoradoV1SensitiveData().tap {
            racialEthnicOrigin = PBSUtils.getRandomEnum(DataActivity)
            religiousBeliefs = PBSUtils.getRandomEnum(DataActivity)
            healthInfo = PBSUtils.getRandomEnum(DataActivity)
            orientation = PBSUtils.getRandomEnum(DataActivity)
            citizenshipStatus = PBSUtils.getRandomEnum(DataActivity)
            geneticId = PBSUtils.getRandomEnum(DataActivity)
            biometricId = PBSUtils.getRandomEnum(DataActivity)
        }
    }

    static UsColoradoV1SensitiveData fromList(List<DataActivity> data) {
        if (data.size() != 7) {
            throw new IllegalArgumentException("Invalid data size. Expected 7 values.")
        }
        new UsColoradoV1SensitiveData().tap {
            racialEthnicOrigin = data[0]
            religiousBeliefs = data[1]
            healthInfo = data[2]
            orientation = data[3]
            citizenshipStatus = data[4]
            geneticId = data[5]
            biometricId = data[6]
        }
    }

    List<Integer> getContentList() {
        [racialEthnicOrigin, religiousBeliefs, healthInfo, orientation,
         citizenshipStatus, geneticId, biometricId]*.value.collect { it ?: 0 }
    }
}
