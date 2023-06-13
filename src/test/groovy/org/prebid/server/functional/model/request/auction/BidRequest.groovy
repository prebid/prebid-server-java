package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonIgnore
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.model.Currency

import static org.prebid.server.functional.model.request.auction.DistributionChannel.APP
import static org.prebid.server.functional.model.request.auction.DistributionChannel.SITE
import static org.prebid.server.functional.model.response.auction.MediaType.VIDEO

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
    List<Currency> cur
    List<String> wlang
    List<String> wlangb
    List<String> bcat
    Integer cattax
    List<String> badv
    List<String> bapp
    Source source
    Regs regs
    BidRequestExt ext

    static BidRequest getDefaultBidRequest(DistributionChannel channel = SITE) {
        getDefaultRequest(channel, Imp.defaultImpression)
    }

    static BidRequest getDefaultVideoRequest(DistributionChannel channel = SITE) {
        getDefaultRequest(channel, Imp.getDefaultImpression(VIDEO))
    }

    static BidRequest getDefaultStoredRequest() {
        getDefaultBidRequest().tap {
            site = null
        }
    }

    private static BidRequest getDefaultRequest(DistributionChannel channel = SITE, Imp imp) {
        new BidRequest().tap {
            it.addImp(imp)
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

    @JsonIgnore
    String getAccountId() {
        site?.publisher?.id ?: app?.publisher?.id
    }

    @JsonIgnore
    void setAccountId(String accountId) {
        if ((!site && !app) || (site && app)) {
            throw new IllegalStateException("Either site or app should be defined")
        }

        if (site) {
            if (!site.publisher) {
                site.publisher = new Publisher()
            }
            site.publisher.id = accountId
        } else {
            if (!app.publisher) {
                app.publisher = new Publisher()
            }
            app.publisher.id = accountId
        }
    }

    void enableCache() {
        if (!ext) {
            ext = new BidRequestExt()
        }
        if (!ext.prebid) {
            ext.prebid = new Prebid()
        }
        if (!ext.prebid.targeting) {
            ext.prebid.targeting = new Targeting()
        }
        if (!ext.prebid.cache) {
            ext.prebid.cache = new PrebidCache()
        }
        if (!ext.prebid.cache.bids) {
            ext.prebid.cache.bids = new PrebidCacheSettings()
        }
        if (!ext.prebid.cache.vastXml) {
            ext.prebid.cache.vastXml = new PrebidCacheSettings()
        }
    }

    void enableEvents() {
        if (!ext) {
            ext = new BidRequestExt()
        }
        if (!ext.prebid) {
            ext.prebid = new Prebid()
        }
        if (!ext.prebid.events) {
            ext.prebid.events = new Events()
        }
    }
}
