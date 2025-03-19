package org.prebid.server.auction.aliases;

import java.util.Map;

public interface AlternateBidderCodesConfig {

    Boolean getEnabled();

    <T extends AlternateBidder> Map<String, T> getBidders();
}
