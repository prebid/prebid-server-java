package org.prebid.server.deals;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.cache.model.DebugHttpCall;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.model.UidWithExpiry;
import org.prebid.server.deals.lineitem.LineItem;
import org.prebid.server.deals.model.User;
import org.prebid.server.deals.model.UserDetails;
import org.prebid.server.deals.model.UserDetailsProperties;
import org.prebid.server.deals.model.UserDetailsRequest;
import org.prebid.server.deals.model.UserDetailsResponse;
import org.prebid.server.deals.model.UserId;
import org.prebid.server.deals.model.UserIdRule;
import org.prebid.server.deals.model.WinEventNotification;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.handler.NotificationEventHandler;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Works with user related information.
 */
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    private static final String USER_SERVICE = "userservice";

    private static final DateTimeFormatter UTC_MILLIS_FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .toFormatter();

    private final LineItemService lineItemService;
    private final HttpClient httpClient;
    private final Clock clock;
    private final Metrics metrics;
    private final JacksonMapper mapper;

    private final String userDetailsUrl;
    private final String winEventUrl;
    private final long timeout;
    private final List<UserIdRule> userIdRules;
    private final String dataCenterRegion;

    public UserService(UserDetailsProperties userDetailsProperties,
                       String dataCenterRegion,
                       LineItemService lineItemService,
                       HttpClient httpClient,
                       Clock clock,
                       Metrics metrics,
                       JacksonMapper mapper) {

        this.lineItemService = Objects.requireNonNull(lineItemService);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.clock = Objects.requireNonNull(clock);
        this.metrics = Objects.requireNonNull(metrics);

        this.userDetailsUrl = Objects.requireNonNull(
                HttpUtil.validateUrl(userDetailsProperties.getUserDetailsEndpoint()));
        this.winEventUrl = Objects.requireNonNull(HttpUtil.validateUrl(userDetailsProperties.getWinEventEndpoint()));
        this.timeout = userDetailsProperties.getTimeout();
        this.userIdRules = Objects.requireNonNull(userDetailsProperties.getUserIds());
        this.dataCenterRegion = Objects.requireNonNull(dataCenterRegion);
        this.mapper = Objects.requireNonNull(mapper);
    }

    /**
     * Fetches {@link UserDetails} from the User Data Store.
     */
    public Future<UserDetails> getUserDetails(AuctionContext context, Timeout timeout) {
        final Map<String, UidWithExpiry> uidsMap = context.getUidsCookie().getCookieUids().getUids();
        if (CollectionUtils.isEmpty(uidsMap.values())) {
            metrics.updateUserDetailsRequestPreparationFailed();
            context.getDebugHttpCalls().put(USER_SERVICE, Collections.singletonList(DebugHttpCall.empty()));
            return Future.succeededFuture(UserDetails.empty());
        }

        final List<UserId> userIds = getUserIds(uidsMap);
        if (CollectionUtils.isEmpty(userIds)) {
            metrics.updateUserDetailsRequestPreparationFailed();
            context.getDebugHttpCalls().put(USER_SERVICE, Collections.singletonList(DebugHttpCall.empty()));
            return Future.succeededFuture(UserDetails.empty());
        }

        final UserDetailsRequest userDetailsRequest = UserDetailsRequest.of(
                UTC_MILLIS_FORMATTER.format(ZonedDateTime.now(clock)), userIds);
        final String body = mapper.encodeToString(userDetailsRequest);

        final long requestTimeout = Math.min(this.timeout, timeout.remaining());

        final long startTime = clock.millis();
        return httpClient.post(userDetailsUrl, body, requestTimeout)
                .map(httpClientResponse -> toUserServiceResult(httpClientResponse, context,
                        userDetailsUrl, body, startTime))
                .recover(throwable -> failGetDetailsResponse(throwable, context, userDetailsUrl, body, startTime));
    }

    /**
     * Retrieves the UID from UIDs Map by each {@link UserIdRule#getLocation()} and if UID is present - creates a
     * {@link UserId} object that contains {@link UserIdRule#getType()} and UID and adds it to UserId list.
     */
    private List<UserId> getUserIds(Map<String, UidWithExpiry> bidderToUid) {
        final List<UserId> userIds = new ArrayList<>();
        for (UserIdRule rule : userIdRules) {
            final UidWithExpiry uid = bidderToUid.get(rule.getLocation());
            if (uid != null) {
                userIds.add(UserId.of(rule.getType(), uid.getUid()));
            }
        }
        return userIds;
    }

    /**
     * Transforms response from User Data Store into {@link Future} of {@link UserDetails}.
     * <p>
     * Throws {@link PreBidException} if an error occurs during response body deserialization.
     */
    private UserDetails toUserServiceResult(HttpClientResponse clientResponse, AuctionContext context,
                                            String requestUrl, String requestBody, long startTime) {
        final int responseStatusCode = clientResponse.getStatusCode();
        verifyStatusCode(responseStatusCode);

        final String responseBody = clientResponse.getBody();
        final User user;
        final int responseTime = responseTime(startTime);
        try {
            user = parseUserDetailsResponse(responseBody);
        } finally {
            context.getDebugHttpCalls().put(USER_SERVICE, Collections.singletonList(
                    DebugHttpCall.builder()
                            .requestUri(requestUrl)
                            .requestBody(requestBody)
                            .responseStatus(responseStatusCode)
                            .responseBody(responseBody)
                            .responseTimeMillis(responseTime)
                            .build()));
        }
        metrics.updateRequestTimeMetric(MetricName.user_details_request_time, responseTime);
        metrics.updateUserDetailsRequestMetric(true);
        return UserDetails.of(user.getData(), user.getExt().getFcapIds());
    }

    private User parseUserDetailsResponse(String responseBody) {
        final UserDetailsResponse userDetailsResponse;
        try {
            userDetailsResponse = mapper.decodeValue(responseBody, UserDetailsResponse.class);
        } catch (DecodeException e) {
            throw new PreBidException(String.format("Cannot parse response: %s", responseBody), e);
        }

        final User user = userDetailsResponse.getUser();
        if (user == null) {
            throw new PreBidException(String.format("Field 'user' is missing in response: %s", responseBody));
        }

        if (user.getData() == null) {
            throw new PreBidException(String.format("Field 'user.data' is missing in response: %s", responseBody));
        }

        if (user.getExt() == null) {
            throw new PreBidException(String.format("Field 'user.ext' is missing in response: %s", responseBody));
        }
        return user;
    }

    /**
     * Throw {@link PreBidException} if response status is not 200.
     */
    private static void verifyStatusCode(int statusCode) {
        if (statusCode != 200) {
            throw new PreBidException(String.format("Bad response status code: %s", statusCode));
        }
    }

    /**
     * Handles errors that occurred during getUserDetails HTTP request or response processing.
     */
    private Future<UserDetails> failGetDetailsResponse(Throwable exception, AuctionContext context, String requestUrl,
                                                       String requestBody, long startTime) {
        final int responseTime = responseTime(startTime);
        context.getDebugHttpCalls().putIfAbsent(USER_SERVICE,
                Collections.singletonList(
                        DebugHttpCall.builder()
                                .requestUri(requestUrl)
                                .requestBody(requestBody)
                                .responseTimeMillis(responseTime)
                                .build()));
        metrics.updateUserDetailsRequestMetric(false);
        metrics.updateRequestTimeMetric(MetricName.user_details_request_time, responseTime);
        logger.warn("Error occurred while fetching user details", exception);
        return Future.failedFuture(exception);
    }

    /**
     * Calculates execution time since the given start time.
     */
    private int responseTime(long startTime) {
        return Math.toIntExact(clock.millis() - startTime);
    }

    /**
     * Accepts lineItemId and bidId from the {@link NotificationEventHandler},
     * joins event data with corresponding Line Item metadata (provided by LineItemService)
     * and passes this information to the User Data Store to facilitate frequency capping.
     */
    public void processWinEvent(String lineItemId, String bidId, UidsCookie uids) {
        final LineItem lineItem = lineItemService.getLineItemById(lineItemId);
        final List<UserId> userIds = getUserIds(uids.getCookieUids().getUids());

        if (!hasRequiredData(lineItem, userIds, lineItemId)) {
            metrics.updateWinRequestPreparationFailed();
            return;
        }

        final String body = mapper.encodeToString(WinEventNotification.builder()
                .bidderCode(lineItem.getSource())
                .bidId(bidId)
                .lineItemId(lineItemId)
                .region(dataCenterRegion)
                .userIds(userIds)
                .winEventDateTime(ZonedDateTime.now(clock))
                .lineUpdatedDateTime(lineItem.getUpdatedTimeStamp())
                .frequencyCaps(lineItem.getFrequencyCaps())
                .build());

        metrics.updateWinNotificationMetric();
        final long startTime = clock.millis();
        httpClient.post(winEventUrl, body, timeout)
                .onComplete(result -> handleWinResponse(result, startTime));
    }

    /**
     * Verify that all necessary data is present and log error if something is missing.
     */
    private static boolean hasRequiredData(LineItem lineItem, List<UserId> userIds, String lineItemId) {
        if (lineItem == null) {
            logger.error("Meta Data for Line Item Id {0} does not exist", lineItemId);
            return false;
        }

        if (CollectionUtils.isEmpty(userIds)) {
            logger.error("User Ids cannot be empty");
            return false;
        }
        return true;
    }

    /**
     * Checks response from User Data Store.
     */
    private void handleWinResponse(AsyncResult<HttpClientResponse> asyncResult, long startTime) {
        metrics.updateWinRequestTime(responseTime(startTime));
        if (asyncResult.succeeded()) {
            try {
                verifyStatusCode(asyncResult.result().getStatusCode());
                metrics.updateWinEventRequestMetric(true);
            } catch (PreBidException e) {
                metrics.updateWinEventRequestMetric(false);
                logWinEventError(e);
            }
        } else {
            metrics.updateWinEventRequestMetric(false);
            logWinEventError(asyncResult.cause());
        }
    }

    /**
     * Logs errors that occurred during processWinEvent HTTP request or bad response code.
     */
    private static void logWinEventError(Throwable exception) {
        logger.warn("Error occurred while pushing win event notification", exception);
    }
}
