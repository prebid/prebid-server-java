package org.prebid.server.functional.util.privacy.gpp.data

import org.prebid.server.functional.util.PBSUtils

class UsColoradoSensitiveData {

    Integer racialEthnicOrigin
    Integer religiousBeliefs
    Integer healthInfo
    Integer orientation
    Integer citizenshipStatus
    Integer geneticId
    Integer biometricId

    static UsColoradoSensitiveData generateRandomSensitiveData() {
        new UsColoradoSensitiveData().tap {
            racialEthnicOrigin = PBSUtils.getRandomNumber(0, 3)
            religiousBeliefs = PBSUtils.getRandomNumber(0, 3)
            healthInfo = PBSUtils.getRandomNumber(0, 3)
            orientation = PBSUtils.getRandomNumber(0, 3)
            citizenshipStatus = PBSUtils.getRandomNumber(0, 3)
            geneticId = PBSUtils.getRandomNumber(0, 3)
            biometricId = PBSUtils.getRandomNumber(0, 3)
        }
    }

    static UsColoradoSensitiveData fromList(List<Integer> data) {
        if (data.size() != 7) {
            throw new IllegalArgumentException("Invalid data size. Expected 7 values.")
        }
        new UsColoradoSensitiveData().tap {
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
         citizenshipStatus, geneticId, biometricId]
    }
}
