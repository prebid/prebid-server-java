package org.prebid.server.functional.model.response.vtrack

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

@ToString(includeNames = true, ignoreNulls = true)
@EqualsAndHashCode
class TransferValue {

    String adm
    Integer width
    Integer height

    static final TransferValue getTransferValue(){
        return new TransferValue().tap {
            adm = PBSUtils.randomString
            width = 300
            height = 250
        }
    }
}
