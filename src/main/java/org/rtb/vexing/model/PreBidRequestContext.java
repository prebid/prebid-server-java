package org.rtb.vexing.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.rtb.vexing.cookie.UidsCookie;
import org.rtb.vexing.model.request.PreBidRequest;

import java.util.List;

@Builder
@ToString
@EqualsAndHashCode
@AllArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PUBLIC)
public class PreBidRequestContext {

    List<Bidder> bidders;

    PreBidRequest preBidRequest;

    UidsCookie uidsCookie;

    long timeout;

    Integer secure;

    String referer;

    String domain;

    String ua;

    String ip;

    boolean isDebug;

    boolean noLiveUids;
}
