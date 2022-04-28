package org.prebid.server.functional.model.response.auction

import com.fasterxml.jackson.annotation.JsonGetter
import com.fasterxml.jackson.annotation.JsonSetter
import groovy.transform.ToString
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.testcontainers.Dependencies
import org.prebid.server.functional.util.PBSUtils

@ToString(includeNames = true, ignoreNulls = true)
class Bid {

    String id
    String impid
    BigDecimal price
    String nurl
    String burl
    String lurl
    Adm adm
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

    static List<Bid> getDefaultBids(List<Imp> imps) {
        imps.collect { getDefaultBid(it) }
    }

    static Bid getDefaultBid(Imp imp) {
        new Bid().tap {
            id = UUID.randomUUID()
            impid = imp.id
            price = PBSUtils.getRandomPrice()
            crid = 1
            h = imp.banner && imp.banner.format ? imp.banner.format.first().h : null
            w = imp.banner && imp.banner.format ? imp.banner.format.first().w : null
        }
    }

    @JsonGetter("adm")
    String getAdm() {
        Dependencies.objectMapperWrapper.encode(adm)
    }

    @JsonSetter("adm")
    void getAdm(String adm) {
        this.adm = Dependencies.objectMapperWrapper.decode(adm, Adm)
    }
}
