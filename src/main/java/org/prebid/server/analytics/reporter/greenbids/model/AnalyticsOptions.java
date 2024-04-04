package org.prebid.server.analytics.reporter.greenbids.model;

import org.prebid.server.analytics.model.AuctionEvent;

public class AnalyticsOptions {
    private String pbuid;
    private Integer sampling;
    private Integer greenbidsSampling;
    private Double exploratorySamplingSplit;
    private String server;

    public AnalyticsOptions(
            AuctionEvent event
    ) {
        this.pbuid =  event.getAuctionContext().getBidRequest().getSite().getPublisher().getId();;
        this.sampling = null;
        this.greenbidsSampling = null;
        this.exploratorySamplingSplit = 0.9;
        this.server = "https://a.greenbids.ai";
    }

    public AnalyticsOptions clone(AuctionEvent event) {
        AnalyticsOptions clone = new AnalyticsOptions(event);
        clone.pbuid = this.pbuid;
        clone.sampling = this.sampling;
        clone.greenbidsSampling = this.greenbidsSampling;
        clone.exploratorySamplingSplit = this.exploratorySamplingSplit;
        clone.server = this.server;
        return clone;
    }
}
