package org.prebid.server.settings.model;

import lombok.Value;
import org.prebid.server.auction.aliases.AlternateBidderCodesConfig;

import java.util.Map;

@Value(staticConstructor = "of")
public class AccountAlternateBidderCodes implements AlternateBidderCodesConfig {

    Boolean enabled;

    Map<String, AccountAlternateBidderCodesBidder> bidders;
}
