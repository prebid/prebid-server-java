package org.prebid.server.functional.model.privacy.gpp

import org.prebid.server.functional.util.PBSUtils

class UsConnecticutV1ChildSensitiveData {

    DataActivity childBlow13
    DataActivity childFrom13to16
    DataActivity childFrom16to18

    static UsConnecticutV1ChildSensitiveData getDefault(DataActivity childBlow13 = DataActivity.NOT_APPLICABLE,
                                                        DataActivity childFrom13to16 = DataActivity.NOT_APPLICABLE,
                                                        DataActivity childFrom16to18 = DataActivity.NOT_APPLICABLE) {

        new UsConnecticutV1ChildSensitiveData().tap {
            it.childBlow13 = childBlow13
            it.childFrom13to16 = childFrom13to16
            it.childFrom16to18 = childFrom16to18
        }
    }

    static UsConnecticutV1ChildSensitiveData getRandom(List<DataActivity> excludedActivities = []) {
        new UsConnecticutV1ChildSensitiveData().tap {
            it.childBlow13 = PBSUtils.getRandomEnum(DataActivity, excludedActivities)
            it.childFrom13to16 = PBSUtils.getRandomEnum(DataActivity, excludedActivities)
            it.childFrom16to18 = PBSUtils.getRandomEnum(DataActivity, excludedActivities)
        }
    }

    List<Integer> getContentList() {
        [childFrom13to16, childBlow13, childFrom16to18]*.value.collect { it ?: 0 }
    }
}
