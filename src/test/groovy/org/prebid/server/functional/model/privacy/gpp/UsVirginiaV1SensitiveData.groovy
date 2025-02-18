package org.prebid.server.functional.model.privacy.gpp

import org.prebid.server.functional.util.PBSUtils

class UsVirginiaV1SensitiveData {

    DataActivity racialEthnicOrigin
    DataActivity religiousBeliefs
    DataActivity healthInfo
    DataActivity orientation
    DataActivity citizenshipStatus
    DataActivity geneticId
    DataActivity biometricId
    DataActivity geolocation

    static UsVirginiaV1SensitiveData generateRandomSensitiveData() {
        new UsVirginiaV1SensitiveData().tap {
            racialEthnicOrigin = PBSUtils.getRandomEnum(DataActivity)
            religiousBeliefs = PBSUtils.getRandomEnum(DataActivity)
            healthInfo = PBSUtils.getRandomEnum(DataActivity)
            orientation = PBSUtils.getRandomEnum(DataActivity)
            citizenshipStatus = PBSUtils.getRandomEnum(DataActivity)
            geneticId = PBSUtils.getRandomEnum(DataActivity)
            biometricId = PBSUtils.getRandomEnum(DataActivity)
            geolocation = PBSUtils.getRandomEnum(DataActivity)
        }
    }

    static UsVirginiaV1SensitiveData fromList(List<DataActivity> data) {
        if (data.size() != 8) {
            throw new IllegalArgumentException("Invalid data size. Expected 8 values.")
        }
        new UsVirginiaV1SensitiveData().tap {
            racialEthnicOrigin = data[0]
            religiousBeliefs = data[1]
            healthInfo = data[2]
            orientation = data[3]
            citizenshipStatus = data[4]
            geneticId = data[5]
            biometricId = data[6]
            geolocation = data[7]
        }
    }

    List<Integer> getContentList() {
        [racialEthnicOrigin, religiousBeliefs, healthInfo, orientation,
         citizenshipStatus, geneticId, biometricId, geolocation]*.value.collect { it ?: 0 }
    }
}
