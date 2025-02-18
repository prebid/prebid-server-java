package org.prebid.server.functional.model.privacy.gpp

import org.prebid.server.functional.util.PBSUtils

class UsCaliforniaV1SensitiveData {

    DataActivity idNumbers
    DataActivity accountInfo
    DataActivity geolocation
    DataActivity racialEthnicOrigin
    DataActivity communicationContents
    DataActivity geneticId
    DataActivity biometricId
    DataActivity healthInfo
    DataActivity orientation

    static UsCaliforniaV1SensitiveData generateRandomSensitiveData() {
        new UsCaliforniaV1SensitiveData().tap {
            idNumbers = PBSUtils.getRandomEnum(DataActivity)
            accountInfo = PBSUtils.getRandomEnum(DataActivity)
            geolocation = PBSUtils.getRandomEnum(DataActivity)
            racialEthnicOrigin = PBSUtils.getRandomEnum(DataActivity)
            communicationContents = PBSUtils.getRandomEnum(DataActivity)
            geneticId = PBSUtils.getRandomEnum(DataActivity)
            biometricId = PBSUtils.getRandomEnum(DataActivity)
            healthInfo = PBSUtils.getRandomEnum(DataActivity)
            orientation = PBSUtils.getRandomEnum(DataActivity)
        }
    }

    static UsCaliforniaV1SensitiveData fromList(List<DataActivity> sensitiveData) {
        if (sensitiveData.size() != 9) {
            throw new IllegalArgumentException("Invalid data size. Expected 9 values.")
        }
        new UsCaliforniaV1SensitiveData().tap {
            idNumbers = sensitiveData[0]
            accountInfo = sensitiveData[1]
            geolocation = sensitiveData[2]
            racialEthnicOrigin = sensitiveData[3]
            communicationContents = sensitiveData[4]
            geneticId = sensitiveData[5]
            biometricId = sensitiveData[6]
            healthInfo = sensitiveData[7]
            orientation = sensitiveData[8]
        }
    }

    List<Integer> getContentList() {
        [idNumbers, accountInfo, geolocation, racialEthnicOrigin,
         communicationContents, geneticId, biometricId, healthInfo, orientation]*.value.collect { it ?: 0 }
    }
}
