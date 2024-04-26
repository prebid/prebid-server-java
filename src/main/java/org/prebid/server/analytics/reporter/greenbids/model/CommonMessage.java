package org.prebid.server.analytics.reporter.greenbids.model;

import org.prebid.server.analytics.model.AuctionEvent;

import java.util.ArrayList;
import java.util.List;

public class CommonMessage {
    public String version;
    public String auctionId;
    public String referrer;
    public double sampling;
    public String prebid;
    public String greenbidsId;
    public String pbuid;
    public String billingId;
    public List<AdUnit> adUnits;
    public Long auctionElapsed;

    public CommonMessage(
        String auctionId,
        AuctionEvent event,
        GreenbidsConfig greenbidsConfig,
        CachedAuction cachedAuction,
        Long auctionElapsed
    ) {
        this.version = "2.2.0";
        this.auctionId = auctionId;
        this.referrer = event.getAuctionContext().getBidRequest().getSite().getPage();
        this.sampling = greenbidsConfig.getGreenbidsSampling();
        this.prebid = "$prebid.version$"; // TODO: to fix
        this.greenbidsId = cachedAuction.greenbidsId;
        this.pbuid = greenbidsConfig.getPbuid();
        this.billingId = cachedAuction.billingId;
        this.adUnits = new ArrayList<>();
        this.auctionElapsed = auctionElapsed;
    }
}
