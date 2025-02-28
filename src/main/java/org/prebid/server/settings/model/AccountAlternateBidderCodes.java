package org.prebid.server.settings.model;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Builder(toBuilder = true)
@Value
public class AccountAlternateBidderCodes {

    Boolean enabled;

    Map<String, AccountAlternateBidderCodesBidder> bidders;
}
