package org.prebid.server.auction;

import org.prebid.server.proto.openrtb.ext.response.Events;
import org.prebid.server.util.HttpUtil;

import java.util.List;
import java.util.Objects;

public class EventsService {

    private static final String EVENT_CALLBACK_URL_PATTERN = "%s/event?type=%s&bidid=%s&bidder=%s";
    private static final String VIEW_EVENT_TYPE = "view";
    private static final String WIN_EVENT_TYPE = "win";

    private List<String> accountsEnabled;
    private String externalUrl;

    public EventsService(List<String> accountsEnabled, String externalUrl) {
        this.accountsEnabled = accountsEnabled;
        this.externalUrl = HttpUtil.validateUrl(Objects.requireNonNull(externalUrl));
    }

    public Events createEvents(String publisherId, String bidId, String bidder) {
        return accountsEnabled != null && accountsEnabled.contains(publisherId)
                ? Events.of(String.format(EVENT_CALLBACK_URL_PATTERN, externalUrl, WIN_EVENT_TYPE, bidId, bidder),
                String.format(EVENT_CALLBACK_URL_PATTERN, externalUrl, VIEW_EVENT_TYPE, bidId, bidder))
                : null;
    }
}
