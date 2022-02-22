package org.prebid.server.functional.model.response.auction

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class SeatBid {

    List<Bid> bid
    String seat
    Integer group
}
