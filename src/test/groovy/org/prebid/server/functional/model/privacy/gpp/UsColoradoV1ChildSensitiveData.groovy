package org.prebid.server.functional.model.privacy.gpp

import org.prebid.server.functional.util.PBSUtils

class UsColoradoV1ChildSensitiveData {

    DataActivity childSensitive

    static UsColoradoV1ChildSensitiveData getDefault(DataActivity childSensitive = DataActivity.NOT_APPLICABLE) {

        new UsColoradoV1ChildSensitiveData().tap {
            it.childSensitive = childSensitive
        }
    }

    static UsColoradoV1ChildSensitiveData getRandom(List<DataActivity> excludedActivities) {
        new UsColoradoV1ChildSensitiveData().tap {
            it.childSensitive = PBSUtils.getRandomEnum(DataActivity, excludedActivities)
        }
    }

    Integer getContentList() {
        this.childSensitive.value
    }
}
