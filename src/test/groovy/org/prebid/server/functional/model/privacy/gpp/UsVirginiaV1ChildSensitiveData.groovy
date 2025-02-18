package org.prebid.server.functional.model.privacy.gpp

import org.prebid.server.functional.util.PBSUtils

class UsVirginiaV1ChildSensitiveData {

    DataActivity childSensitive

    static UsVirginiaV1ChildSensitiveData getDefault(DataActivity childSensitive = DataActivity.NOT_APPLICABLE) {

        new UsVirginiaV1ChildSensitiveData().tap {
            it.childSensitive = childSensitive
        }
    }

    static UsVirginiaV1ChildSensitiveData getRandom(List<DataActivity> excludedActivities = []) {
        new UsVirginiaV1ChildSensitiveData().tap {
            it.childSensitive = PBSUtils.getRandomEnum(DataActivity, excludedActivities)
        }
    }

    Integer getContentList() {
        childSensitive.value
    }
}
