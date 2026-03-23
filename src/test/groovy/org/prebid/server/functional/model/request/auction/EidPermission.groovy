package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.ToString
import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC

@ToString(includeNames = true, ignoreNulls = true)
class EidPermission {

    String source
    String inserter
    String matcher
    @JsonProperty("mm")
    Integer matchMethod
    List<BidderName> bidders = [GENERIC]

    static EidPermission getDefaultEidPermission(List<BidderName> bidders = [GENERIC]) {
        new EidPermission().tap {
            it.source = PBSUtils.randomString
            it.inserter = PBSUtils.randomString
            it.matcher = PBSUtils.randomString
            it.matchMethod = PBSUtils.randomNumber
            it.bidders = bidders
        }
    }

    static EidPermission from(Eid eid, List<BidderName> bidders = [GENERIC]) {
        new EidPermission().tap {
            it.source = eid.source
            it.inserter = eid.inserter
            it.matcher = eid.matcher
            it.matchMethod = eid.matchMethod
            it.bidders = bidders
        }
    }
}
