package org.prebid.server.auction.model;

import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.User;
import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class BidderPrivacyResult {

    String requestBidder;

    User user;

    Device device;

    boolean blockedRequestByTcf;

    boolean blockedAnalyticsByTcf;
}

