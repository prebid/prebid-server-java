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

    public CommonMessage(
        String auctionId,
        AuctionEvent event,
        double samplingRate,
        CachedAuction cachedAuction
    ) {
        this.version = "2.2.0";
        this.auctionId = auctionId;
        this.referrer = event.getAuctionContext().getBidRequest().getSite().getPage();
        this.sampling = samplingRate;
        this.prebid = "$prebid.version$"; // TODO: to fix
        this.greenbidsId = cachedAuction.greenbidsId;
        this.pbuid = event.getAuctionContext().getBidRequest().getSite().getPublisher().getId();
        this.billingId = cachedAuction.billingId;
        this.adUnits = new ArrayList<>();
    }
}
