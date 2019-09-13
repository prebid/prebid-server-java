package org.prebid.server.events;

import org.prebid.server.proto.openrtb.ext.response.Events;
import org.prebid.server.util.HttpUtil;

import java.util.Objects;

public class EventsService {

    private static final String EVENT_CALLBACK_URL_PATTERN = "%s/event?t=%s&b=%s&a=%s&f=i";
    private static final String WIN_EVENT_TYPE = "win";
    private static final String IMP_EVENT_TYPE = "imp";
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
                String.format(EVENT_CALLBACK_URL_PATTERN, externalUrl, WIN_EVENT_TYPE, bidId, accountId),
                String.format(EVENT_CALLBACK_URL_PATTERN, externalUrl, IMP_EVENT_TYPE, bidId, accountId));
    }

    /**
     * Returns value for hb_winurl targeting keyword.
     */
    public String winUrlTargeting(String accountId) {
        return String.format(EVENT_CALLBACK_URL_PATTERN, externalUrl, WIN_EVENT_TYPE, BIDID_PLACEHOLDER, accountId);
    }
}
