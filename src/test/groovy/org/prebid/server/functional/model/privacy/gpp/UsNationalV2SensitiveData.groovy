package org.prebid.server.functional.model.privacy.gpp

import org.prebid.server.functional.util.PBSUtils

class UsNationalV2SensitiveData extends UsNationalV1SensitiveData {

    DataActivity consumerHealthData
    DataActivity crimeVictim
    DataActivity nationalOrigin
    DataActivity transgenderStatus

    static UsNationalV2SensitiveData generateRandomSensitiveData() {
        new UsNationalV2SensitiveData().tap {
            racialEthnicOrigin = PBSUtils.getRandomEnum(DataActivity)
            religiousBeliefs = PBSUtils.getRandomEnum(DataActivity)
            healthInfo = PBSUtils.getRandomEnum(DataActivity)
            orientation = PBSUtils.getRandomEnum(DataActivity)
            citizenshipStatus = PBSUtils.getRandomEnum(DataActivity)
            geneticId = PBSUtils.getRandomEnum(DataActivity)
            biometricId = PBSUtils.getRandomEnum(DataActivity)
            geolocation = PBSUtils.getRandomEnum(DataActivity)
            idNumbers = PBSUtils.getRandomEnum(DataActivity)
            accountInfo = PBSUtils.getRandomEnum(DataActivity)
            unionMembership = PBSUtils.getRandomEnum(DataActivity)
            communicationContents = PBSUtils.getRandomEnum(DataActivity)
            consumerHealthData = PBSUtils.getRandomEnum(DataActivity)
            crimeVictim = PBSUtils.getRandomEnum(DataActivity)
            nationalOrigin = PBSUtils.getRandomEnum(DataActivity)
            transgenderStatus = PBSUtils.getRandomEnum(DataActivity)
        }
    }

    static UsNationalV2SensitiveData fromList(List<DataActivity> data) {
        if (data.size() != 16) {
            throw new IllegalArgumentException("Invalid data size. Expected 16 values.")
        }
        new UsNationalV2SensitiveData().tap {
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
            consumerHealthData = data[12]
            crimeVictim = data[13]
            nationalOrigin = data[14]
            transgenderStatus = data[15]
        }
    }

    @Override
    List<Integer> getContentList() {
        [racialEthnicOrigin, religiousBeliefs, healthInfo, orientation,
         citizenshipStatus, geneticId, biometricId, geolocation,
         idNumbers, accountInfo, unionMembership, communicationContents,
         consumerHealthData, crimeVictim, nationalOrigin, transgenderStatus]*.value.collect { it ?: 0 }
    }
}
