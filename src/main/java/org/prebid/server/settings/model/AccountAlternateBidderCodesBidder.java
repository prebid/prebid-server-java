package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.AlternateBidder;

import java.util.Set;

@Builder(toBuilder = true)
@Value
public class AccountAlternateBidderCodesBidder implements AlternateBidder {

    Boolean enabled;

    @JsonAlias("allowed-bidder-codes")
    Set<String> allowedBidderCodes;
}
