package org.prebid.server.events;

import org.apache.commons.lang3.BooleanUtils;
import org.prebid.server.proto.openrtb.ext.response.Events;
import org.prebid.server.settings.model.Account;
import org.prebid.server.util.HttpUtil;

import java.util.Objects;

public class EventsService {

    private static final String EVENT_CALLBACK_URL_PATTERN = "%s/event?type=%s&bidid=%s&bidder=%s";
    private static final String VIEW_EVENT_TYPE = "view";
    private static final String WIN_EVENT_TYPE = "win";

    private final String externalUrl;

    public EventsService(String externalUrl) {
        this.externalUrl = HttpUtil.validateUrl(Objects.requireNonNull(externalUrl));
    }

    public Events createEvent(String bidId, String bidder) {
        return Events.of(
                String.format(EVENT_CALLBACK_URL_PATTERN, externalUrl, WIN_EVENT_TYPE, bidId, bidder),
                String.format(EVENT_CALLBACK_URL_PATTERN, externalUrl, VIEW_EVENT_TYPE, bidId, bidder));
    }

    /**
     * Returns events enabled flag for the given account.
     * <p>
     * This data is not critical, so returns false if null.
     */
    public Boolean isEventsEnabled(Account account) {
        return BooleanUtils.toBoolean(account.getEventsEnabled());
    }
}
