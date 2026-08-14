package org.prebid.server.functional.model.bidder

import groovy.transform.EqualsAndHashCode
import org.prebid.server.functional.util.PBSUtils

@EqualsAndHashCode
class Ix implements BidderAdapter {

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
