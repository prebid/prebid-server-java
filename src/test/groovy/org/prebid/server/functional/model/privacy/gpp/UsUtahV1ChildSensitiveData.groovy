package org.prebid.server.functional.model.privacy.gpp

class UsUtahV1ChildSensitiveData {

    GppDataActivity childSensitive

    static UsUtahV1ChildSensitiveData getDefault(GppDataActivity childSensitive = GppDataActivity.NOT_APPLICABLE) {

        new UsUtahV1ChildSensitiveData().tap {
            it.childSensitive = childSensitive
        }
    }

    Integer getContentList() {
        childSensitive.value
    }
}
