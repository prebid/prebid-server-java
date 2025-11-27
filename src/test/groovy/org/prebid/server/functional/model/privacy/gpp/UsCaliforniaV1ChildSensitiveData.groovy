package org.prebid.server.functional.model.privacy.gpp

import org.prebid.server.functional.util.PBSUtils

class UsCaliforniaV1ChildSensitiveData {

    GppDataActivity childUnder13
    GppDataActivity childFrom13to16

    static UsCaliforniaV1ChildSensitiveData getDefault(GppDataActivity childUnder13 = GppDataActivity.NOT_APPLICABLE,
                                                       GppDataActivity childFrom13to16 = GppDataActivity.NOT_APPLICABLE) {

        new UsCaliforniaV1ChildSensitiveData().tap {
            it.childUnder13 = childUnder13
            it.childFrom13to16 = childFrom13to16
        }
    }

    static UsCaliforniaV1ChildSensitiveData getRandom(List<GppDataActivity> excludedActivities) {
        new UsCaliforniaV1ChildSensitiveData().tap {
            it.childUnder13 = PBSUtils.getRandomEnum(GppDataActivity, excludedActivities)
            it.childFrom13to16 = PBSUtils.getRandomEnum(GppDataActivity, excludedActivities)
        }
    }

    List<Integer> getContentList() {
        [childFrom13to16, childUnder13]*.value.collect { it ?: 0 }
    }
}
