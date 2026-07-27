package org.prebid.server.functional.model.privacy.gpp

class UsVirginiaV1ChildSensitiveData {

    GppDataActivity childSensitive

    static UsVirginiaV1ChildSensitiveData getDefault(GppDataActivity childSensitive = GppDataActivity.NOT_APPLICABLE) {

        new UsVirginiaV1ChildSensitiveData().tap {
            it.childSensitive = childSensitive
        }
    }

    Integer getContentList() {
        childSensitive.value
    }
}
