package org.prebid.server.analytics.reporter.greenbids.model;

import org.prebid.server.auction.model.AuctionContext;

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
            AuctionContext auctionContext,
            GreenbidsConfig greenbidsConfig,
            Long auctionElapsed,
            String greenbidsId,
            String billingId
    ) {
        this.version = "2.2.0";
        this.auctionId = auctionContext.getBidRequest().getId();
        this.referrer = auctionContext.getBidRequest().getSite().getPage();
        this.sampling = greenbidsConfig.getGreenbidsSampling();
        this.prebid = "$prebid.version$"; // TODO: to fix
        this.greenbidsId = greenbidsId;
        this.pbuid = greenbidsConfig.getPbuid();
        this.billingId = billingId;
        this.adUnits = new ArrayList<>();
        this.auctionElapsed = auctionElapsed;
    }
}
