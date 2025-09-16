package org.prebid.server.bidder.alvads;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import org.prebid.server.proto.openrtb.ext.response.BidType;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class ExtBidAlvads {

    private BidType crtype;
}
