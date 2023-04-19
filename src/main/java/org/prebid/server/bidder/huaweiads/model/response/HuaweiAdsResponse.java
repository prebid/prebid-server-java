package org.prebid.server.bidder.huaweiads.model.response;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class HuaweiAdsResponse {

    Integer retcode;

    String reason;

    List<Ad30> multiad;
}
