package org.prebid.server.events;

import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.proto.openrtb.ext.response.Events;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.util.HttpUtil;

import java.util.Objects;

public class EventsService {

    private static final Logger logger = LoggerFactory.getLogger(EventsService.class);

    private static final String EVENT_CALLBACK_URL_PATTERN = "%s/event?type=%s&bidid=%s&bidder=%s";
    private static final String VIEW_EVENT_TYPE = "view";
    private static final String WIN_EVENT_TYPE = "win";

    private final ApplicationSettings applicationSettings;
    private final String externalUrl;

    public EventsService(ApplicationSettings applicationSettings, String externalUrl) {
        this.applicationSettings = Objects.requireNonNull(applicationSettings);
        this.externalUrl = HttpUtil.validateUrl(Objects.requireNonNull(externalUrl));
    }

    public Events createEvent(String bidId, String bidder) {
        return Events.of(
                String.format(EVENT_CALLBACK_URL_PATTERN, externalUrl, WIN_EVENT_TYPE, bidId, bidder),
                String.format(EVENT_CALLBACK_URL_PATTERN, externalUrl, VIEW_EVENT_TYPE, bidId, bidder));
    }

    public Future<Boolean> isEventsEnabled(String publisherId, Timeout timeout) {
        return applicationSettings.getAccountById(publisherId, timeout)
                .map(account -> ObjectUtils.defaultIfNull(account.getEventsEnabled(), false))
                .otherwise(EventsService::fallbackResult);
    }

    private static boolean fallbackResult(Throwable exception) {
        if (!(exception instanceof PreBidException)) {
            logger.warn("Error occurred while fetching account", exception);
        }
        return false;
    }
}
