package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonIgnore
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

import static org.prebid.server.functional.model.request.auction.DistributionChannel.APP
import static org.prebid.server.functional.model.request.auction.DistributionChannel.SITE

@EqualsAndHashCode
@ToString(includeNames = true, ignoreNulls = true)
class BidRequest {

    String id
    List<Imp> imp
    Site site
    App app
    Device device
    User user
    Integer test
    Integer at
    Long tmax
    List<String> wseat
    List<String> bseat
    Integer allimps
    List<String> cur
    List<String> wlang
    List<String> bcat
    List<String> badv
    List<String> bapp
    Source source
    Regs regs
    BidRequestExt ext

    static BidRequest getDefaultBidRequest(DistributionChannel channel = SITE) {
        new BidRequest().tap {
            it.addImp(Imp.defaultImpression)
            regs = Regs.defaultRegs
            id = UUID.randomUUID()
            tmax = 2500
            ext = new BidRequestExt(prebid: new Prebid(debug: 1))
            if (channel == SITE) {
                site = Site.defaultSite
            }
            if (channel == APP) {
                app = App.defaultApp
            }
        }
    }

    static BidRequest getDefaultStoredRequest() {
        getDefaultBidRequest().tap {
            site = null
        }
    }

    void addImp(Imp impression) {
        if (imp == null) {
            imp = []
        }
        imp.add(impression)
    }

    @JsonIgnore
    List<String> getRequestBidders() {
        def bidderList = []
        def bidder = imp?.first()?.ext?.prebid?.bidder
        if (bidder) {
            bidderList = bidder.configuredBidders
        }
        bidderList
    }

    void enableCache() {
        if (ext == null) {
            ext = new BidRequestExt()
        }
        if (ext.prebid == null) {
            ext.prebid = new Prebid()
        }
        if (ext.prebid.targeting == null) {
            ext.prebid.targeting = new Targeting()
        }
        if (ext.prebid.cache == null) {
            ext.prebid.cache = new PrebidCache()
        }
        if (ext.prebid.cache.bids == null) {
            ext.prebid.cache.bids = new PrebidCacheSettings()
        }
        if (ext.prebid.cache.vastXml == null) {
            ext.prebid.cache.vastXml = new PrebidCacheSettings()
        }
    }
}
