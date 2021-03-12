package org.prebid.server.analytics.model;

import lombok.Builder;
import lombok.Value;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.proto.response.VideoResponse;

import java.util.List;

/**
 * Represents a transaction at /openrtb2/video endpoint.
 */
@Builder(toBuilder = true)
@Value
public class VideoEvent {

    Integer status;

    List<String> errors;

    HttpContext httpContext;

    AuctionContext auctionContext;

    VideoResponse bidResponse;
}

