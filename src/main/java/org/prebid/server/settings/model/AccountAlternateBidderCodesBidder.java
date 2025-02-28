package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Builder;
import lombok.Value;

import java.util.Set;

@Builder(toBuilder = true)
@Value
public class AccountAlternateBidderCodesBidder {

    Boolean enabled;

    @JsonAlias("allowedbiddercodes")
    Set<String> allowedBidderCodes;
}
