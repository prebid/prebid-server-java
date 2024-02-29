package org.prebid.server.functional.util.privacy.gpp.data

import org.prebid.server.functional.util.PBSUtils

class UsUtahSensitiveData {

    int racialEthnicOrigin
    int religiousBeliefs
    int orientation
    int citizenshipStatus
    int healthInfo
    int geneticId
    int biometricId
    int geolocation

    static UsUtahSensitiveData generateRandomSensitiveData() {
        new UsUtahSensitiveData().tap {
            racialEthnicOrigin = PBSUtils.getRandomNumber(0, 2)
            religiousBeliefs = PBSUtils.getRandomNumber(0, 2)
            orientation = PBSUtils.getRandomNumber(0, 2)
            citizenshipStatus = PBSUtils.getRandomNumber(0, 2)
            healthInfo = PBSUtils.getRandomNumber(0, 2)
            geneticId = PBSUtils.getRandomNumber(0, 2)
            biometricId = PBSUtils.getRandomNumber(0, 2)
            geolocation = PBSUtils.getRandomNumber(0, 2)
        }
    }

    static UsUtahSensitiveData fromList(List<Integer> data) {
        if (data.size() != 8) {
            throw new IllegalArgumentException("Invalid data size. Expected 8 values.")
        }
        new UsUtahSensitiveData().tap {
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
         healthInfo, geneticId, biometricId, geolocation]
    }
}
