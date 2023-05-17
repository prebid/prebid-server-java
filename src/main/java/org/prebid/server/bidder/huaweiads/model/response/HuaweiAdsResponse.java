package org.prebid.server.bidder.huaweiads.model.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class HuaweiAdsResponse {

    Integer retcode;

    String reason;

    List<Ad30> multiad;
}
