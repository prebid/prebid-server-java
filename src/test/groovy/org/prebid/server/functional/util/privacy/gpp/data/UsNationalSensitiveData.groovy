package org.prebid.server.functional.util.privacy.gpp.data

import org.prebid.server.functional.util.PBSUtils

class UsNationalSensitiveData {

    int racialEthnicOrigin
    int religiousBeliefs
    int healthInfo
    int orientation
    int citizenshipStatus
    int geneticId
    int biometricId
    int geolocation
    int idNumbers
    int accountInfo
    int unionMembership
    int communicationContents

    static UsNationalSensitiveData generateRandomSensitiveData() {
        new UsNationalSensitiveData().tap {
            racialEthnicOrigin = PBSUtils.getRandomNumber(0, 3)
            religiousBeliefs = PBSUtils.getRandomNumber(0, 3)
            healthInfo = PBSUtils.getRandomNumber(0, 3)
            orientation = PBSUtils.getRandomNumber(0, 3)
            citizenshipStatus = PBSUtils.getRandomNumber(0, 3)
            geneticId = PBSUtils.getRandomNumber(0, 3)
            biometricId = PBSUtils.getRandomNumber(0, 3)
            geolocation = PBSUtils.getRandomNumber(0, 3)
            idNumbers = PBSUtils.getRandomNumber(0, 3)
            accountInfo = PBSUtils.getRandomNumber(0, 3)
            unionMembership = PBSUtils.getRandomNumber(0, 3)
            communicationContents = PBSUtils.getRandomNumber(0, 3)
        }
    }

    static UsNationalSensitiveData fromList(List<Integer> data) {
        if (data.size() != 12) {
            throw new IllegalArgumentException("Invalid data size. Expected 12 values.")
        }
        new UsNationalSensitiveData().tap {
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
         idNumbers, accountInfo, unionMembership, communicationContents]
    }
}
