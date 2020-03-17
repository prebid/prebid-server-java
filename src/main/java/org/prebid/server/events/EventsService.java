package org.prebid.server.events;

import org.prebid.server.proto.openrtb.ext.response.Events;
import org.prebid.server.util.HttpUtil;

import java.util.Objects;

public class EventsService {

    private static final String BIDID_PLACEHOLDER = "BIDID";

    private final String externalUrl;

    public EventsService(String externalUrl) {
        this.externalUrl = HttpUtil.validateUrl(Objects.requireNonNull(externalUrl));
    }

    /**
     * Returns {@link Events} object based on given params.
     */
    public Events createEvent(String bidId, String accountId) {
        return Events.of(
                eventUrl(EventRequest.Type.win, bidId, accountId, EventRequest.Format.image),
                eventUrl(EventRequest.Type.imp, bidId, accountId, EventRequest.Format.image));
    }

    /**
     * Returns value for "hb_winurl" targeting keyword.
     */
    public String winUrlTargeting(String accountId) {
        return eventUrl(EventRequest.Type.win, BIDID_PLACEHOLDER, accountId, EventRequest.Format.image);
    }

    /**
     * Returns url for win tracking.
     */
    public String winUrl(String bidId, String accountId) {
        return eventUrl(EventRequest.Type.win, bidId, accountId, EventRequest.Format.image);
    }

    /**
     * Returns url for VAST tracking.
     */
    public String vastUrlTracking(String bidId, String accountId) {
        return eventUrl(EventRequest.Type.imp, bidId, accountId, EventRequest.Format.blank);
    }

    private String eventUrl(EventRequest.Type type, String bidId, String accountId, EventRequest.Format format) {
        final EventRequest eventRequest = EventRequest.builder()
                .type(type)
                .bidId(bidId)
                .accountId(accountId)
                .format(format)
                .build();

        return EventUtil.toUrl(externalUrl, eventRequest);
    }
}
