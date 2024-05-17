package org.prebid.server.functional.model.response.auction

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.model.request.auction.Asset
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.util.ObjectMapperWrapper
import org.prebid.server.functional.util.PBSUtils

@ToString(includeNames = true, ignoreNulls = true)
@EqualsAndHashCode
class Bid implements ObjectMapperWrapper {

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
    String tactic
    Integer cattax
    List<String> cat
    List<Integer> attr
    List<Integer> apis
    Integer api
    Integer protocol
    Integer qagmediarating
    String language
    String langb
    String dealid
    Integer w
    Integer h
    Integer wratio
    Integer hratio
    Integer exp
    Integer dur
    Integer mtype
    Integer slotinpod
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
            if (imp.nativeObj || imp.video) {
                adm = new Adm(assets: [Asset.defaultAsset])
            }
        }
    }

    void setAdm(Object adm) {
        if (adm instanceof Adm) {
            this.adm = encode(adm)
        } else if (adm instanceof String) {
            this.adm = adm
        } else {
            this.adm = null
        }
    }
}
