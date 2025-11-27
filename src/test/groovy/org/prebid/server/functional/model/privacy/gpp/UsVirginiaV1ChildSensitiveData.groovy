package org.prebid.server.functional.model.privacy.gpp

import org.prebid.server.functional.util.PBSUtils

class UsVirginiaV1ChildSensitiveData {

    GppDataActivity childSensitive

    static UsVirginiaV1ChildSensitiveData getDefault(GppDataActivity childSensitive = GppDataActivity.NOT_APPLICABLE) {

        new UsVirginiaV1ChildSensitiveData().tap {
            it.childSensitive = childSensitive
        }
    }

    static UsVirginiaV1ChildSensitiveData getRandom(List<GppDataActivity> excludedActivities = []) {
        new UsVirginiaV1ChildSensitiveData().tap {
            it.childSensitive = PBSUtils.getRandomEnum(GppDataActivity, excludedActivities)
        }
    }

    Integer getContentList() {
        childSensitive.value
    }
}
