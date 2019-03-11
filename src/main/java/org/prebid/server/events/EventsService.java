package org.prebid.server.events;

import io.vertx.core.Future;
import org.prebid.server.events.account.AccountEventsService;
import org.prebid.server.execution.Timeout;
import org.prebid.server.proto.openrtb.ext.response.Events;
import org.prebid.server.util.HttpUtil;

import java.util.Objects;

public class EventsService {

    private static final String EVENT_CALLBACK_URL_PATTERN = "%s/event?type=%s&bidid=%s&bidder=%s";
    private static final String VIEW_EVENT_TYPE = "view";
    private static final String WIN_EVENT_TYPE = "win";

    private AccountEventsService accountEventsService;
    private String externalUrl;

    public EventsService(AccountEventsService accountEventsService, String externalUrl) {
        this.accountEventsService = Objects.requireNonNull(accountEventsService);
        this.externalUrl = HttpUtil.validateUrl(Objects.requireNonNull(externalUrl));
    }

    public Future<Events> createEvents(String publisherId, String bidId, String bidder, Timeout timeout) {
        return accountEventsService.eventsEnabled(publisherId, timeout)
                .map(eventsEnabled -> eventsEnabled
                        ? Events.of(
                        String.format(EVENT_CALLBACK_URL_PATTERN, externalUrl, WIN_EVENT_TYPE, bidId, bidder),
                        String.format(EVENT_CALLBACK_URL_PATTERN, externalUrl, VIEW_EVENT_TYPE, bidId, bidder))
                        : Events.empty())
                .recover(ex -> Future.succeededFuture(Events.empty()));
    }

}
