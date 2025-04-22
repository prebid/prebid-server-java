package org.prebid.server.auction.aliases;

import java.util.Map;

public interface AlternateBidderCodesConfig {

    Boolean getEnabled();

    Map<String, ? extends AlternateBidder> getBidders();
}
