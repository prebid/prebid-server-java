package org.prebid.server.functional.model.response.auction

import groovy.transform.ToString
import org.prebid.server.functional.model.request.auction.DsaTransparency
import org.prebid.server.functional.util.PBSUtils

@ToString(includeNames = true, ignoreNulls = true)
class BidExtDsa {

    String behalf
    String paid
    List<DsaTransparency> transparency
    Integer adrender

    static BidExtDsa getDefaultBidExtDsa() {
        new BidExtDsa(
                behalf: PBSUtils.randomString,
                paid: PBSUtils.randomString,
                adrender: PBSUtils.getRandomNumber(0, 2),
                transparency: [DsaTransparency.defaultRegsDsaTransparency]
        )
    }
}
