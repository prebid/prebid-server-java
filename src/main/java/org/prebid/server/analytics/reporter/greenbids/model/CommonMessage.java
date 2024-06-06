package org.prebid.server.analytics.reporter.greenbids.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class CommonMessage {

    private final String version;

    private final String auctionId;

    private final String referrer;

    private final double sampling;

    private final String prebidServer;

    private final String greenbidsId;

    private final String pbuid;

    private final String billingId;

    private final List<GreenbidsAdUnit> adUnits;

    private final Long auctionElapsed;
}
