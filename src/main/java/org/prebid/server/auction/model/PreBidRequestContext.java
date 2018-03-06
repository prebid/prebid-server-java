package org.prebid.server.auction.model;

import lombok.Builder;
import lombok.Value;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.execution.GlobalTimeout;
import org.prebid.server.proto.request.PreBidRequest;

import java.util.List;

@Builder
@Value
public class PreBidRequestContext {

    List<AdapterRequest> adapterRequests;

    PreBidRequest preBidRequest;

    UidsCookie uidsCookie;

    GlobalTimeout timeout;

    String domain;

    String referer;

    String ip;

    String ua;

    Integer secure;

    boolean isDebug;

    boolean noLiveUids;
}
