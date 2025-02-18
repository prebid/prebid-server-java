package org.prebid.server.functional.model.privacy.gpp

import org.prebid.server.functional.util.PBSUtils

class UsNationalV2ChildSensitiveData {

    DataActivity childBlow13
    DataActivity childFrom13to16
    DataActivity childFrom16to17

    static UsNationalV2ChildSensitiveData getDefault(DataActivity childBlow13 = DataActivity.NOT_APPLICABLE,
                                                     DataActivity childFrom13to16 = DataActivity.NOT_APPLICABLE,
                                                     DataActivity childFrom16to17 = DataActivity.NOT_APPLICABLE) {

        new UsNationalV2ChildSensitiveData().tap {
            it.childBlow13 = childBlow13
            it.childFrom13to16 = childFrom13to16
            it.childFrom16to17 = childFrom16to17
        }
    }

    static UsNationalV2ChildSensitiveData getRandom(List<DataActivity> excludedActivities) {
        new UsNationalV2ChildSensitiveData().tap {
            it.childBlow13 = PBSUtils.getRandomEnum(DataActivity, excludedActivities)
            it.childFrom13to16 = PBSUtils.getRandomEnum(DataActivity, excludedActivities)
            it.childFrom16to17 = PBSUtils.getRandomEnum(DataActivity, excludedActivities)
        }
    }

    List<Integer> getContentList() {
        [childFrom13to16, childBlow13, childFrom16to17]*.value.collect { it ?: 0 }
    }
}
