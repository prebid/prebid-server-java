package org.prebid.server.analytics.reporter.greenbids.model;

import org.prebid.server.analytics.model.AuctionEvent;

import java.util.ArrayList;
import java.util.List;

public class CommonMessage {
    private final String version;
    private final String auctionId;
    private final String referrer;
    private final double sampling;
    private final String prebid;
    private final String greenbidsId;
    private final String pbuid;
    private final String billingId;
    private final List<AdUnit> adUnits;

    public CommonMessage(
        String auctionId,
        //AnalyticsOptions analyticsOptions,
        AuctionEvent event,
        double samplingRate,
        CachedAuction cachedAuction
    ) {
        this.version = "2.2.0";
        this.auctionId = auctionId;
        this.referrer = event.getAuctionContext().getBidRequest().getSite().getRef();
        this.sampling = samplingRate;
        this.prebid = "$prebid.version$"; // TODO: to fix
        this.greenbidsId = cachedAuction.greenbidsId;
        this.pbuid = event.getAuctionContext().getBidRequest().getSite().getPublisher().getId();
        this.billingId = cachedAuction.billingId;
        this.adUnits = new ArrayList<>();
    }
}
