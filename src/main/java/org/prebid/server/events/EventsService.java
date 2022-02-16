package org.prebid.server.events;

import org.prebid.server.proto.openrtb.ext.response.Events;
import org.prebid.server.util.HttpUtil;

import java.util.Objects;

public class EventsService {

    private final String externalUrl;

    public EventsService(String externalUrl) {
        this.externalUrl = HttpUtil.validateUrl(Objects.requireNonNull(externalUrl));
    }

    /**
     * Returns {@link Events} object based on given params.
     */
    public Events createEvent(String bidId,
                              String bidder,
                              String accountId,
                              String lineItemId,
                              boolean analyticsEnabled,
                              EventsContext eventsContext) {
        return Events.of(
                eventUrl(
                        EventRequest.Type.WIN,
                        bidId,
                        bidder,
                        accountId,
                        lineItemId,
                        analytics(analyticsEnabled),
                        EventRequest.Format.IMAGE,
                        eventsContext),
                eventUrl(
                        EventRequest.Type.IMP,
                        bidId,
                        bidder,
                        accountId,
                        lineItemId,
                        analytics(analyticsEnabled),
                        EventRequest.Format.IMAGE,
                        eventsContext));
    }

    /**
     * Returns url for win tracking.
     */
    public String winUrl(String bidId, String bidder, String accountId, String lineItemId,
                         boolean analyticsEnabled, EventsContext eventsContext) {
        return eventUrl(
                EventRequest.Type.WIN,
                bidId,
                bidder,
                accountId,
                lineItemId,
                analytics(analyticsEnabled),
                EventRequest.Format.IMAGE,
                eventsContext);
    }

    /**
     * Returns url for VAST tracking.
     */
    public String vastUrlTracking(String bidId,
                                  String bidder,
                                  String accountId,
                                  String lineItemId,
                                  EventsContext eventsContext) {
        return eventUrl(EventRequest.Type.IMP,
                bidId,
                bidder,
                accountId,
                lineItemId,
                null,
                EventRequest.Format.BLANK,
                eventsContext);
    }

    private String eventUrl(EventRequest.Type type,
                            String bidId,
                            String bidder,
                            String accountId,
                            String lineItemId,
                            EventRequest.Analytics analytics,
                            EventRequest.Format format,
                            EventsContext eventsContext) {

        final EventRequest eventRequest = EventRequest.builder()
                .type(type)
                .bidId(bidId)
                .auctionId(eventsContext.getAuctionId())
                .accountId(accountId)
                .bidder(bidder)
                .timestamp(eventsContext.getAuctionTimestamp())
                .format(format)
                .integration(eventsContext.getIntegration())
                .lineItemId(lineItemId)
                .analytics(analytics)
                .build();

        return EventUtil.toUrl(externalUrl, eventRequest);
    }

    private static EventRequest.Analytics analytics(boolean analyticsEnabled) {
        return analyticsEnabled ? null : EventRequest.Analytics.DISABLED;
    }
}
