package org.prebid.server.functional.model.request.auction

import groovy.transform.EqualsAndHashCode
import org.prebid.server.functional.util.PBSUtils

@EqualsAndHashCode
class Ix {

    String siteId
    List<Integer> size
    String sid

    static Ix getDefault() {
        new Ix().tap {
            siteId = PBSUtils.randomString
            size = [PBSUtils.randomNumber, PBSUtils.randomNumber]
            sid = PBSUtils.randomString
        }
    }
}
