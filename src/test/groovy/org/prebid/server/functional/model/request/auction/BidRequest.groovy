package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonIgnore
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.model.Currency
import org.prebid.server.functional.model.response.auction.MediaType

import static org.prebid.server.functional.model.request.auction.DebugCondition.ENABLED
import static org.prebid.server.functional.model.request.auction.DistributionChannel.APP
import static org.prebid.server.functional.model.request.auction.DistributionChannel.DOOH
import static org.prebid.server.functional.model.request.auction.DistributionChannel.SITE
import static org.prebid.server.functional.model.response.auction.MediaType.AUDIO
import static org.prebid.server.functional.model.response.auction.MediaType.NATIVE
import static org.prebid.server.functional.model.response.auction.MediaType.VIDEO

@EqualsAndHashCode
@ToString(includeNames = true, ignoreNulls = true)
class BidRequest {

    String id
    List<Imp> imp
    Site site
    App app
    Dooh dooh
    Device device
    User user
    DebugCondition test
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

    static BidRequest getDefaultNativeRequest(DistributionChannel channel = SITE) {
        getDefaultRequest(channel, Imp.getDefaultImpression(NATIVE))
    }

    static BidRequest getDefaultAudioRequest(DistributionChannel channel = SITE) {
        getDefaultRequest(channel, Imp.getDefaultImpression(AUDIO))
    }

    static BidRequest getDefaultBidRequest(MediaType mediaType, DistributionChannel channel = SITE) {
        getDefaultRequest(channel, Imp.getDefaultImpression(mediaType))
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
            ext = new BidRequestExt(prebid: new Prebid(debug: ENABLED))
            if (channel == SITE) {
                site = Site.defaultSite
            }
            if (channel == APP) {
                app = App.defaultApp
            }
            if (channel == DOOH) {
                dooh = Dooh.defaultDooh
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
    List<DistributionChannel> getRequestDistributionChannels() {
        [app, dooh, site].collectMany { it != null ? [DistributionChannel.findByValue(it.class.simpleName)] : [] }
    }

    @JsonIgnore
    String getAccountId() {
        app?.publisher?.id ?: dooh?.publisher?.id ?: site?.publisher?.id
    }

    @JsonIgnore
    void setAccountId(String accountId) {
        if ((!dooh && !site && !app) || (site && app) || (dooh && site) || (app && dooh)) {
            throw new IllegalStateException("Either site, app or dooh should be defined")
        }

        if (site) {
            if (!site.publisher) {
                site.publisher = new Publisher()
            }
            site.publisher.id = accountId
        } else if (app) {
            if (!app.publisher) {
                app.publisher = new Publisher()
            }
            app.publisher.id = accountId
        } else {
            if (!dooh.publisher) {
                dooh.publisher = new Publisher()
            }
            dooh.publisher.id = accountId
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

    void enabledReturnAllBidStatus() {
        if (!ext) {
            ext = new BidRequestExt()
        }
        if (!ext.prebid) {
            ext.prebid = new Prebid()
        }
        if (!ext.prebid.returnAllBidStatus) {
            ext.prebid.returnAllBidStatus = true
        }
    }
}
