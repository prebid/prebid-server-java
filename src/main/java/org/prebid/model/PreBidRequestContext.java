package org.prebid.model;

import lombok.Builder;
import lombok.Value;
import org.prebid.cookie.UidsCookie;
import org.prebid.execution.GlobalTimeout;
import org.prebid.model.request.PreBidRequest;

import java.util.List;

@Builder
@Value
public final class PreBidRequestContext {

    List<Bidder> bidders;

    PreBidRequest preBidRequest;

    UidsCookie uidsCookie;

    GlobalTimeout timeout;

    Integer secure;

    String referer;

    String domain;

    String ua;

    String ip;

    boolean isDebug;

    boolean noLiveUids;
}
