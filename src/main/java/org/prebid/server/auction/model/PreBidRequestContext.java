package org.prebid.server.auction.model;

import lombok.Builder;
import lombok.Value;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.execution.GlobalTimeout;
import org.prebid.server.proto.request.PreBidRequest;

import java.util.List;

@Builder
@Value
public final class PreBidRequestContext {

    List<AdapterRequest> adapterRequests;

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
