package org.prebid.server.functional.model.privacy.gpp

import org.prebid.server.functional.util.PBSUtils

class UsCaliforniaV1ChildSensitiveData {

    GppDataActivity toSellUnder16
    GppDataActivity toShareUnder16

    static UsCaliforniaV1ChildSensitiveData getDefault(GppDataActivity childUnder13 = GppDataActivity.NOT_APPLICABLE,
                                                       GppDataActivity childFrom13to16 = GppDataActivity.NOT_APPLICABLE) {

        new UsCaliforniaV1ChildSensitiveData().tap {
            it.toSellUnder16 = childUnder13
            it.toShareUnder16 = childFrom13to16
        }
    }

    static UsCaliforniaV1ChildSensitiveData getRandom(List<GppDataActivity> excludedActivities) {
        new UsCaliforniaV1ChildSensitiveData().tap {
            it.toSellUnder16 = PBSUtils.getRandomEnum(GppDataActivity, excludedActivities)
            it.toShareUnder16 = PBSUtils.getRandomEnum(GppDataActivity, excludedActivities)
        }
    }

    List<Integer> getContentList() {
        [toShareUnder16, toSellUnder16]*.value.collect { it ?: 0 }
    }
}
