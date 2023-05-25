package org.prebid.server.bidder.flipp.model.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder(toBuilder = true)
@Getter
public class Inline {

    Integer adId;

    Integer advertiserId;

    Integer campaignId;

    String clickUrl;

    List<Content> contents;

    Integer creativeId;

    Integer flightId;

    Integer height;

    String impressionUrl;

    Prebid prebid;

    Integer priorityId;

    Integer width;
}
