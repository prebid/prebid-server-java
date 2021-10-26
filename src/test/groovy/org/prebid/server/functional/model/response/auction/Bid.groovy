package org.prebid.server.functional.model.response.auction

import groovy.transform.ToString
import org.prebid.server.functional.model.request.auction.Imp

@ToString(includeNames = true, ignoreNulls = true)
class Bid {

    String id
    String impid
    BigDecimal price
    String nurl
    String burl
    String lurl
    String adm
    String adid
    List<String> adomain
    String bundle
    String iurl
    String cid
    String crid
    List<String> cat
    List<Integer> attr
    Integer api
    Integer protocol
    Integer qagmediarating
    String language
    String dealid
    Integer w
    Integer h
    Integer wratio
    Integer hratio
    Integer exp
    BidExt ext

    static Bid getDefaultBid(Imp imp) {
        getDefaultBid(imp.id)
    }

    static Bid getDefaultBid(String impId) {
        new Bid().tap {
            id = UUID.randomUUID()
            impid = impId
            price = 1.23
            crid = 1
        }
    }
}
