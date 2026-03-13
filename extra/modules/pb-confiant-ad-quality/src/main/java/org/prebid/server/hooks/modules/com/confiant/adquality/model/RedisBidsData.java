package org.prebid.server.hooks.modules.com.confiant.adquality.model;

import com.iab.openrtb.request.BidRequest;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class RedisBidsData {

    BidRequest breq;

    List<RedisBidResponseData> bresps;
}
