package org.prebid.server.functional.model.response.auction

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.model.request.auction.Asset
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.util.ObjectMapperWrapper
import org.prebid.server.functional.util.PBSUtils

import static groovy.lang.Closure.DELEGATE_FIRST

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
    @JsonProperty("qagmediarating")
    Integer qagMediaRating
    String language
    String langb
    String dealid
    @JsonProperty("w")
    Integer weight
    @JsonProperty("h")
    Integer height
    @JsonProperty("wratio")
    Integer weightRatio
    @JsonProperty("hratio")
    Integer heightRatio
    Integer exp
    Integer dur
    @JsonProperty("mtype")
    BidMediaType mediaType
    Integer slotinpod
    BidExt ext

    static List<Bid> getDefaultBids(List<Imp> imps) {
        imps.collect { getDefaultBid(it) }
    }

    static Bid getDefaultBid(Imp imp) {
        new Bid().tap {
            id = UUID.randomUUID()
            impid = imp.id
            price = imp.bidFloor != null ? imp.bidFloor : PBSUtils.getRandomPrice()
            crid = 1
            height = imp.banner && imp.banner.format ? imp.banner.format.first().height : null
            weight = imp.banner && imp.banner.format ? imp.banner.format.first().weight : null
            if (imp.nativeObj || imp.video) {
                adm = new Adm(assets: [Asset.defaultAsset])
            }
        }
    }

    static List<Bid> getDefaultMultyTypesBids(Imp imp, @DelegatesTo(Bid) Closure commonInit = null) {
        List<Bid> bids = []
        if (imp.banner) bids << createBid(imp, BidMediaType.BANNER) { adm = null }
        if (imp.video) bids << createBid(imp, BidMediaType.VIDEO)
        if (imp.nativeObj) bids << createBid(imp, BidMediaType.NATIVE)
        if (imp.audio) bids << createBid(imp, BidMediaType.AUDIO) { adm = null }

        if (commonInit) {
            bids.each { bid ->
                commonInit.delegate = bid
                commonInit.resolveStrategy = DELEGATE_FIRST
                commonInit()
            }
        }
        bids
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

    private static Bid createBid(Imp imp, BidMediaType type, @DelegatesTo(Bid) Closure init = null) {
        def bid = getDefaultBid(imp)
        bid.mediaType = type
        if (init) {
            init.delegate = bid
            init.resolveStrategy = DELEGATE_FIRST
            init()
        }
        bid
    }
}
