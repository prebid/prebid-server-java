package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Value;
import org.prebid.server.auction.aliases.AlternateBidder;

import java.util.Set;

@Value(staticConstructor = "of")
public class AccountAlternateBidderCodesBidder implements AlternateBidder {

    Boolean enabled;

    @JsonAlias("allowed-bidder-codes")
    Set<String> allowedBidderCodes;
}
