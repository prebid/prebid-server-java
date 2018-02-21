package org.rtb.vexing.model;

import lombok.Builder;
import lombok.Value;
import org.rtb.vexing.cookie.UidsCookie;
import org.rtb.vexing.execution.GlobalTimeout;
import org.rtb.vexing.model.request.PreBidRequest;

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
