package org.prebid.server.functional.model.privacy.gpp

import org.prebid.server.functional.util.PBSUtils

class UsNationalV1ChildSensitiveData {

    DataActivity childBlow13
    DataActivity childFrom13to16

    static UsNationalV1ChildSensitiveData getDefault(DataActivity childBlow13 = DataActivity.NOT_APPLICABLE,
                                                     DataActivity childFrom13to16 = DataActivity.NOT_APPLICABLE) {

        new UsNationalV1ChildSensitiveData().tap {
            it.childBlow13 = childBlow13
            it.childFrom13to16 = childFrom13to16
        }
    }

    static UsNationalV1ChildSensitiveData getRandom(List<DataActivity> excludedActivities) {
        new UsNationalV1ChildSensitiveData().tap {
            it.childBlow13 = PBSUtils.getRandomEnum(DataActivity, excludedActivities)
            it.childFrom13to16 = PBSUtils.getRandomEnum(DataActivity, excludedActivities)
        }
    }

    List<Integer> getContentList() {
        [childFrom13to16, childBlow13]*.value.collect { it ?: 0 }
    }
}
