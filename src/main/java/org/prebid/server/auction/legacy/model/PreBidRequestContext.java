package org.prebid.server.auction.legacy.model;

import lombok.Builder;
import lombok.Value;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.execution.Timeout;
import org.prebid.server.proto.request.legacy.PreBidRequest;

import java.util.List;

@Deprecated
@Builder
@Value
public class PreBidRequestContext {

    List<AdapterRequest> adapterRequests;

    PreBidRequest preBidRequest;

    UidsCookie uidsCookie;

    Timeout timeout;

    String domain;

    String referer;

    String ip;

    String ua;

    Integer secure;

    boolean isDebug;

    boolean noLiveUids;
}
