package org.prebid.server.functional.util.privacy.gpp.data

import org.prebid.server.functional.util.PBSUtils

class UsCaliforniaSensitiveData {

    int idNumbers
    int accountInfo
    int geolocation
    int racialEthnicOrigin
    int communicationContents
    int geneticId
    int biometricId
    int healthInfo
    int orientation

    static UsCaliforniaSensitiveData generateRandomSensitiveData() {
        new UsCaliforniaSensitiveData().tap {
            idNumbers = PBSUtils.getRandomNumber(0, 2)
            accountInfo = PBSUtils.getRandomNumber(0, 2)
            geolocation = PBSUtils.getRandomNumber(0, 2)
            racialEthnicOrigin = PBSUtils.getRandomNumber(0, 2)
            communicationContents = PBSUtils.getRandomNumber(0, 2)
            geneticId = PBSUtils.getRandomNumber(0, 2)
            biometricId = PBSUtils.getRandomNumber(0, 2)
            healthInfo = PBSUtils.getRandomNumber(0, 2)
            orientation = PBSUtils.getRandomNumber(0, 2)
        }
    }

    static UsCaliforniaSensitiveData fromList(List<Integer> sensitiveData) {
        if (sensitiveData.size() != 9) {
            throw new IllegalArgumentException("Invalid data size. Expected 9 values.")
        }
        new UsCaliforniaSensitiveData().tap {
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
         communicationContents, geneticId, biometricId, healthInfo, orientation]
    }
}
