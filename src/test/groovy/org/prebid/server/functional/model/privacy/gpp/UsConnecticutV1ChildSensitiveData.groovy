package org.prebid.server.functional.model.privacy.gpp

class UsConnecticutV1ChildSensitiveData {

    GppDataActivity childUnder13
    GppDataActivity childFrom13to16
    GppDataActivity childFrom13to16Targeted

    static UsConnecticutV1ChildSensitiveData getDefault(GppDataActivity childUnder13 = GppDataActivity.NOT_APPLICABLE,
                                                        GppDataActivity childFrom13to16 = GppDataActivity.NOT_APPLICABLE,
                                                        GppDataActivity childFrom16to18 = GppDataActivity.NOT_APPLICABLE) {

        new UsConnecticutV1ChildSensitiveData().tap {
            it.childUnder13 = childUnder13
            it.childFrom13to16 = childFrom13to16
            it.childFrom13to16Targeted = childFrom16to18
        }
    }

    List<Integer> getContentList() {
        [childUnder13, childFrom13to16, childFrom13to16Targeted]*.value.collect { it ?: 0 }
    }
}
