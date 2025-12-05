package org.prebid.server.functional.model.privacy.gpp

import org.prebid.server.functional.util.PBSUtils

class UsNationalV2ChildSensitiveData extends UsNationalV1ChildSensitiveData {

    GppDataActivity childFrom16to17

    static UsNationalV2ChildSensitiveData getDefault(GppDataActivity childUnder13 = GppDataActivity.NOT_APPLICABLE,
                                                     GppDataActivity childFrom13to16 = GppDataActivity.NOT_APPLICABLE,
                                                     GppDataActivity childFrom16to17 = GppDataActivity.NOT_APPLICABLE) {

        new UsNationalV2ChildSensitiveData().tap {
            it.childUnder13 = childUnder13
            it.childFrom13to16 = childFrom13to16
            it.childFrom16to17 = childFrom16to17
        }
    }

    List<Integer> getContentList() {
        [childFrom13to16, childUnder13, childFrom16to17]*.value.collect { it ?: 0 }
    }
}
