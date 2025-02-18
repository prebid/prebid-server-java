package org.prebid.server.functional.model.privacy.gpp

import org.prebid.server.functional.util.PBSUtils

class UsUtahV1ChildSensitiveData {

    DataActivity childSensitive

    static UsUtahV1ChildSensitiveData getDefault(DataActivity childSensitive = DataActivity.NOT_APPLICABLE) {

        new UsUtahV1ChildSensitiveData().tap {
            it.childSensitive = childSensitive
        }
    }

    static UsUtahV1ChildSensitiveData getRandom(List<DataActivity> excludedActivities) {
        new UsUtahV1ChildSensitiveData().tap {
            it.childSensitive = PBSUtils.getRandomEnum(DataActivity, excludedActivities)
        }
    }

    Integer getContentList() {
        childSensitive.value
    }
}
