package org.prebid.server.functional.util.privacy.gpp.data

import org.prebid.server.functional.util.PBSUtils

class UsConnecticutSensitiveData {

    int racialEthnicOrigin
    int religiousBeliefs
    int healthInfo
    int orientation
    int citizenshipStatus
    int geneticId
    int biometricId
    int geolocation
    int idNumbers

    static UsConnecticutSensitiveData generateRandomSensitiveData() {
        new UsConnecticutSensitiveData().tap {
            racialEthnicOrigin = PBSUtils.getRandomNumber(0, 2)
            religiousBeliefs = PBSUtils.getRandomNumber(0, 2)
            healthInfo = PBSUtils.getRandomNumber(0, 2)
            orientation = PBSUtils.getRandomNumber(0, 2)
            citizenshipStatus = PBSUtils.getRandomNumber(0, 2)
            geneticId = PBSUtils.getRandomNumber(0, 2)
            biometricId = PBSUtils.getRandomNumber(0, 2)
            geolocation = PBSUtils.getRandomNumber(0, 2)
            idNumbers = PBSUtils.getRandomNumber(0, 2)
        }
    }

    static UsConnecticutSensitiveData fromList(List<Integer> data) {
        if (data.size() != 9) {
            throw new IllegalArgumentException("Invalid data size. Expected 9 values.")
        }
        new UsConnecticutSensitiveData().tap {
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
         citizenshipStatus, geneticId, biometricId, geolocation, idNumbers]
    }
}
