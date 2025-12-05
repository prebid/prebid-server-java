package org.prebid.server.functional.model.privacy.gpp

import org.prebid.server.functional.util.PBSUtils

class UsNationalV1ChildSensitiveData {

    GppDataActivity childUnder13
    GppDataActivity childFrom13to16

    static UsNationalV1ChildSensitiveData getDefault(GppDataActivity childUnder13 = GppDataActivity.NOT_APPLICABLE,
                                                     GppDataActivity childFrom13to16 = GppDataActivity.NOT_APPLICABLE) {

        new UsNationalV1ChildSensitiveData().tap {
            it.childUnder13 = childUnder13
            it.childFrom13to16 = childFrom13to16
        }
    }

    List<Integer> getContentList() {
        [childFrom13to16, childUnder13]*.value.collect { it ?: 0 }
    }
}
