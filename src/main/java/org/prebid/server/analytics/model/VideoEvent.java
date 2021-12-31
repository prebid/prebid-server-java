package org.prebid.server.analytics.model;

import lombok.Builder;
import lombok.Value;
import org.prebid.server.analytics.AnalyticsEvent;
import org.prebid.server.analytics.AnalyticsEventProcessor;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.proto.response.VideoResponse;

import java.util.List;

/**
 * Represents a transaction at /openrtb2/video endpoint.
 */
@Builder(toBuilder = true)
@Value
public class VideoEvent implements AnalyticsEvent {

    Integer status;

    List<String> errors;

    HttpRequestContext httpContext;

    AuctionContext auctionContext;

    VideoResponse bidResponse;

    @Override
    public <T> T accept(AnalyticsEventProcessor<T> processor) {
        return processor.processVideoEvent(this);
    }
}

