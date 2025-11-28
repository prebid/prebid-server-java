package org.prebid.server.functional.model.privacy.gpp

import org.prebid.server.functional.util.PBSUtils

class UsUtahV1ChildSensitiveData {

    GppDataActivity childSensitive

    static UsUtahV1ChildSensitiveData getDefault(GppDataActivity childSensitive = GppDataActivity.NOT_APPLICABLE) {

        new UsUtahV1ChildSensitiveData().tap {
            it.childSensitive = childSensitive
        }
    }

    static UsUtahV1ChildSensitiveData getRandom(List<GppDataActivity> excludedActivities) {
        new UsUtahV1ChildSensitiveData().tap {
            it.childSensitive = PBSUtils.getRandomEnum(GppDataActivity, excludedActivities)
        }
    }

    Integer getContentList() {
        childSensitive.value
    }
}
