package org.prebid.server.functional.model.privacy.gpp

class UsColoradoV1ChildSensitiveData {

    GppDataActivity childSensitive

    static UsColoradoV1ChildSensitiveData getDefault(GppDataActivity childSensitive = GppDataActivity.NOT_APPLICABLE) {
        new UsColoradoV1ChildSensitiveData().tap {
            it.childSensitive = childSensitive
        }
    }

    Integer getContentList() {
        this.childSensitive.value
    }
}
