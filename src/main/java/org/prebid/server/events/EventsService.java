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
    public Events createEvent(String bidId, String bidder, String accountId, Long timestamp, String integration) {
        return Events.of(
                eventUrl(
                        EventRequest.Type.win,
                        bidId,
                        bidder,
                        accountId,
                        timestamp,
                        EventRequest.Format.image,
                        integration),
                eventUrl(
                        EventRequest.Type.imp,
                        bidId,
                        bidder,
                        accountId,
                        timestamp,
                        EventRequest.Format.image,
                        integration));
    }

    /**
     * Returns url for win tracking.
     */
    public String winUrl(String bidId, String bidder, String accountId, Long timestamp, String integration) {
        return eventUrl(
                EventRequest.Type.win, bidId, bidder, accountId, timestamp, EventRequest.Format.image, integration);
    }

    /**
     * Returns url for VAST tracking.
     */
    public String vastUrlTracking(String bidId, String bidder, String accountId, Long timestamp, String integration) {
        return eventUrl(
                EventRequest.Type.imp, bidId, bidder, accountId, timestamp, EventRequest.Format.blank, integration);
    }

    private String eventUrl(EventRequest.Type type,
                            String bidId,
                            String bidder,
                            String accountId,
                            Long timestamp,
                            EventRequest.Format format,
                            String integration) {

        final EventRequest eventRequest = EventRequest.builder()
                .type(type)
                .bidId(bidId)
                .accountId(accountId)
                .bidder(bidder)
                .timestamp(timestamp)
                .format(format)
                .integration(integration)
                .build();

        return EventUtil.toUrl(externalUrl, eventRequest);
    }
}
