package org.prebid.server.deals;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.cache.model.CacheHttpRequest;
import org.prebid.server.cache.model.DebugHttpCall;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.model.UidWithExpiry;
import org.prebid.server.cookie.proto.Uids;
import org.prebid.server.deals.lineitem.LineItem;
import org.prebid.server.deals.model.ExtUser;
import org.prebid.server.deals.model.Segment;
import org.prebid.server.deals.model.User;
import org.prebid.server.deals.model.UserData;
import org.prebid.server.deals.model.UserDetails;
import org.prebid.server.deals.model.UserDetailsProperties;
import org.prebid.server.deals.model.UserDetailsRequest;
import org.prebid.server.deals.model.UserDetailsResponse;
import org.prebid.server.deals.model.UserId;
import org.prebid.server.deals.model.UserIdRule;
import org.prebid.server.deals.model.WinEventNotification;
import org.prebid.server.deals.proto.FrequencyCap;
import org.prebid.server.deals.proto.LineItemMetaData;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class UserServiceTest extends VertxTest {

    private static final DateTimeFormatter UTC_MILLIS_FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .toFormatter();

    private static final String USER_DETAILS_ENDPOINT = "http://user-data.com";
    private static final String WIN_EVENT_ENDPOINT = "http://win-event.com";
    private static final String DATA_CENTER_REGION = "region";
    private static final long CONFIG_TIMEOUT = 300L;
    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private LineItemService lineItemService;
    @Mock
    private HttpClient httpClient;
    @Mock
    private Metrics metrics;

    private List<UserIdRule> userIdRules;
    private Clock clock;

    private UserService userService;

    private UidsCookie uidsCookie;
    private AuctionContext auctionContext;
    private Timeout timeout;
    private ZonedDateTime now;

    @Before
    public void setUp() {
        clock = Clock.fixed(Instant.parse("2019-07-26T10:00:00Z"), ZoneOffset.UTC);
        now = ZonedDateTime.now(clock);
        userIdRules = singletonList(UserIdRule.of("khaos", "uid", "rubicon"));

        userService = new UserService(
                UserDetailsProperties.of(USER_DETAILS_ENDPOINT, WIN_EVENT_ENDPOINT, CONFIG_TIMEOUT, userIdRules),
                DATA_CENTER_REGION,
                lineItemService,
                httpClient,
                clock,
                metrics,
                jacksonMapper);

        uidsCookie = new UidsCookie(Uids.builder()
                .uids(singletonMap("rubicon", new UidWithExpiry("uid", null)))
                .build(), jacksonMapper);
        auctionContext = AuctionContext.builder().uidsCookie(uidsCookie).debugHttpCalls(new HashMap<>()).build();

        timeout = new TimeoutFactory(Clock.fixed(Instant.now(), ZoneId.systemDefault())).create(500L);
    }

    @Test
    public void getUserDetailsShouldReturnEmptyUserDetailsWhenUidsAreEmpty() {
        // given
        final UidsCookie uidsCookie = new UidsCookie(Uids.builder().uids(emptyMap()).build(), jacksonMapper);
        final AuctionContext context = AuctionContext.builder().uidsCookie(uidsCookie)
                .debugHttpCalls(new HashMap<>()).build();

        // when
        final UserDetails result = userService.getUserDetails(context, timeout).result();

        // then
        verify(metrics).updateUserDetailsRequestPreparationFailed();
        verifyNoInteractions(httpClient);

        assertEquals(UserDetails.empty(), result);
    }

    @Test
    public void getUserDetailsShouldReturnReturnEmptyUserDetailsWhenUidsDoesNotContainRuleLocation() {
        // given
        final List<UserIdRule> ruleWithMissingLocation = singletonList(
                UserIdRule.of("khaos", "uid", "bad_location"));

        userService = new UserService(
                UserDetailsProperties.of(
                        USER_DETAILS_ENDPOINT, WIN_EVENT_ENDPOINT, CONFIG_TIMEOUT, ruleWithMissingLocation),
                DATA_CENTER_REGION,
                lineItemService,
                httpClient,
                clock,
                metrics,
                jacksonMapper);

        // when
        final UserDetails result = userService.getUserDetails(auctionContext, timeout).result();

        // then
        verify(metrics).updateUserDetailsRequestPreparationFailed();
        verifyNoInteractions(httpClient);

        assertEquals(UserDetails.empty(), result);
    }

    @Test
    public void getUserDetailsShouldSendPostRequestWithExpectedParameters() throws IOException {
        // given
        given(httpClient.post(anyString(), anyString(), anyLong())).willReturn(Future.failedFuture("something"));

        // when
        userService.getUserDetails(auctionContext, timeout);

        // then
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(httpClient).post(eq(USER_DETAILS_ENDPOINT), captor.capture(), eq(CONFIG_TIMEOUT));

        final UserDetailsRequest capturedRequest = mapper.readValue(captor.getValue(), UserDetailsRequest.class);

        assertThat(ZonedDateTime.parse(capturedRequest.getTime())).isEqualTo(UTC_MILLIS_FORMATTER.format(now));
        assertThat(capturedRequest.getIds()).hasSize(1)
                .containsOnly(UserId.of("khaos", "uid"));
        verify(metrics).updateUserDetailsRequestMetric(eq(false));
        verify(metrics).updateRequestTimeMetric(eq(MetricName.user_details_request_time), anyLong());
    }

    @Test
    public void getUserDetailsShouldUseRemainingGlobalTimeoutIfTimeoutFromConfigurationIsGreaterThanRemaining() {
        // given
        given(httpClient.post(anyString(), anyString(), anyLong())).willReturn(Future.failedFuture("something"));

        userService = new UserService(
                UserDetailsProperties.of(USER_DETAILS_ENDPOINT, WIN_EVENT_ENDPOINT, 600L, userIdRules),
                DATA_CENTER_REGION,
                lineItemService,
                httpClient,
                clock,
                metrics,
                jacksonMapper);

        // when
        userService.getUserDetails(auctionContext, timeout);

        // then
        verify(metrics).updateUserDetailsRequestMetric(eq(false));
        verify(metrics).updateRequestTimeMetric(eq(MetricName.user_details_request_time), anyLong());
        verify(httpClient).post(anyString(), anyString(), eq(500L));
    }

    @Test
    public void getUserDetailsShouldReturnFailedFutureWhenResponseStatusIsNotOk() {
        // given
        given(httpClient.post(anyString(), anyString(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(400, null, null)));

        // when
        final Future<UserDetails> result = userService.getUserDetails(auctionContext, timeout);

        // then
        verify(metrics).updateRequestTimeMetric(eq(MetricName.user_details_request_time), anyLong());
        verify(metrics).updateUserDetailsRequestMetric(eq(false));
        verify(httpClient).post(eq(USER_DETAILS_ENDPOINT), anyString(), eq(CONFIG_TIMEOUT));

        assertTrue(result.failed());
        assertThat(result.cause())
                .isInstanceOf(PreBidException.class)
                .hasMessage("Bad response status code: 400");
    }

    @Test
    public void getUserDetailsShouldReturnFailedFutureWhenErrorOccurs() {
        // given
        given(httpClient.post(anyString(), anyString(), anyLong()))
                .willReturn(Future.failedFuture(new TimeoutException("Timeout has been exceeded")));

        // when
        final Future<UserDetails> result = userService.getUserDetails(auctionContext, timeout);

        // then
        verify(metrics).updateUserDetailsRequestMetric(eq(false));
        verify(metrics).updateRequestTimeMetric(eq(MetricName.user_details_request_time), anyLong());
        verify(httpClient).post(eq(USER_DETAILS_ENDPOINT), anyString(), eq(CONFIG_TIMEOUT));

        assertTrue(result.failed());
        assertThat(result.cause())
                .isInstanceOf(TimeoutException.class)
                .hasMessage("Timeout has been exceeded");
    }

    @Test
    public void getUserDetailsShouldReturnFailedFutureWhenResponseBodyDecodingFails() {
        // given
        given(httpClient.post(anyString(), anyString(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(200, null, "invalid_body")));

        // when
        final Future<UserDetails> result = userService.getUserDetails(auctionContext, timeout);

        // then
        verify(metrics).updateUserDetailsRequestMetric(eq(false));
        verify(metrics).updateRequestTimeMetric(eq(MetricName.user_details_request_time), anyLong());
        verify(httpClient).post(eq(USER_DETAILS_ENDPOINT), anyString(), eq(CONFIG_TIMEOUT));

        assertTrue(result.failed());
        assertThat(result.cause())
                .isInstanceOf(PreBidException.class)
                .hasMessage("Cannot parse response: invalid_body");
    }

    @Test
    public void getUserDetailsShouldReturnExpectedResult() {
        // given
        final UserDetailsResponse response = UserDetailsResponse.of(User.of(
                asList(
                        UserData.of("1", "rubicon", asList(Segment.of("2222"), Segment.of("3333"))),
                        UserData.of("2", "bluekai", asList(Segment.of("5555"), Segment.of("6666")))),
                ExtUser.of(asList("L-1111", "O-2222"))));

        given(httpClient.post(anyString(), anyString(), anyLong())).willReturn(
                Future.succeededFuture(HttpClientResponse.of(200, null, jacksonMapper.encodeToString(response))));

        // when
        final UserDetails result = userService.getUserDetails(auctionContext, timeout).result();

        // then
        verify(metrics).updateUserDetailsRequestMetric(eq(true));
        verify(metrics).updateRequestTimeMetric(eq(MetricName.user_details_request_time), anyLong());
        verify(httpClient).post(eq(USER_DETAILS_ENDPOINT), anyString(), eq(CONFIG_TIMEOUT));

        final UserDetails expectedDetails = UserDetails.of(
                asList(UserData.of("1", "rubicon", asList(Segment.of("2222"), Segment.of("3333"))),
                        UserData.of("2", "bluekai", asList(Segment.of("5555"), Segment.of("6666")))),
                asList("L-1111", "O-2222"));

        assertEquals(expectedDetails, result);
    }

    @Test
    public void getUserDetailsShouldReturnFailedFutureWhenUserInResponseIsNull() {
        // given
        final UserDetailsResponse response = UserDetailsResponse.of(null);

        given(httpClient.post(anyString(), anyString(), anyLong())).willReturn(
                Future.succeededFuture(HttpClientResponse.of(200, null, jacksonMapper.encodeToString(response))));

        // when
        final Future<UserDetails> result = userService.getUserDetails(auctionContext, timeout);

        // then
        verify(metrics).updateUserDetailsRequestMetric(eq(false));
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).isInstanceOf(PreBidException.class)
                .hasMessage("Field 'user' is missing in response: {}");
    }

    @Test
    public void getUserDetailsShouldReturnFailedFutureWhenUserDataInResponseIsNull() {
        // given
        final UserDetailsResponse response = UserDetailsResponse.of(User.of(
                null, ExtUser.of(asList("L-1111", "O-2222"))));

        given(httpClient.post(anyString(), anyString(), anyLong())).willReturn(
                Future.succeededFuture(HttpClientResponse.of(200, null, jacksonMapper.encodeToString(response))));

        // when
        final Future<UserDetails> result = userService.getUserDetails(auctionContext, timeout);

        // then
        verify(metrics).updateUserDetailsRequestMetric(eq(false));
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).isInstanceOf(PreBidException.class)
                .hasMessage("Field 'user.data' is missing in response: {\"user\":{\"ext\":{\"fcapIds\":[\"L-1111\","
                        + "\"O-2222\"]}}}");
    }

    @Test
    public void getUserDetailsShouldReturnFailedFutureWhenExtUserInResponseIsNull() {
        // given
        final UserDetailsResponse response = UserDetailsResponse.of(User.of(
                singletonList(UserData.of("2", "bluekai", singletonList(Segment.of("6666")))), null));

        given(httpClient.post(anyString(), anyString(), anyLong())).willReturn(
                Future.succeededFuture(HttpClientResponse.of(200, null, jacksonMapper.encodeToString(response))));

        // when
        final Future<UserDetails> result = userService.getUserDetails(auctionContext, timeout);

        // then
        verify(metrics).updateUserDetailsRequestMetric(eq(false));
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).isInstanceOf(PreBidException.class)
                .hasMessage("Field 'user.ext' is missing in response: {\"user\":{\"data\":[{\"id\":\"2\",\"name\":"
                        + "\"bluekai\",\"segment\":[{\"id\":\"6666\"}]}]}}");
    }

    @Test
    public void getUserDetailsShouldAddEmptyCachedHttpCallWhenUidsAreNotDefined() {
        // given
        final UidsCookie uidsCookie = new UidsCookie(Uids.builder().uids(emptyMap()).build(), jacksonMapper);
        final AuctionContext context = AuctionContext.builder().uidsCookie(uidsCookie)
                .debugHttpCalls(new HashMap<>()).build();

        // when
        userService.getUserDetails(context, timeout).result();

        // then
        assertThat(context.getDebugHttpCalls()).hasSize(1)
                .containsEntry("userservice", singletonList(DebugHttpCall.empty()));
    }

    @Test
    public void getUserDetailsShouldAddEmptyCachedHttpCallWhenUserIdsAreNotDefined() {
        // given
        final List<UserIdRule> ruleWithMissingLocation = singletonList(
                UserIdRule.of("khaos", "uid", "bad_location"));

        userService = new UserService(
                UserDetailsProperties.of(
                        USER_DETAILS_ENDPOINT, WIN_EVENT_ENDPOINT, CONFIG_TIMEOUT, ruleWithMissingLocation),
                DATA_CENTER_REGION,
                lineItemService,
                httpClient,
                clock,
                metrics,
                jacksonMapper);

        // when
        userService.getUserDetails(auctionContext, timeout).result();

        // then
        assertThat(auctionContext.getDebugHttpCalls()).hasSize(1)
                .containsEntry("userservice", singletonList(DebugHttpCall.empty()));
    }

    @Test
    public void getUserDetailsShouldAddCachedHttpCallWhenThrowsPrebidException() throws JsonProcessingException {
        // given
        given(httpClient.post(anyString(), anyString(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(200, null, "invalid_body")));

        // when
        userService.getUserDetails(auctionContext, timeout);

        // then
        assertThat(auctionContext.getDebugHttpCalls()).hasSize(1)
                .containsEntry("userservice", singletonList(DebugHttpCall.builder()
                        .requestUri("http://user-data.com")
                        .requestBody(mapper.writeValueAsString(UserDetailsRequest.of(UTC_MILLIS_FORMATTER.format(now),
                                singletonList(UserId.of("khaos", "uid")))))
                        .responseStatus(200)
                        .responseBody("invalid_body")
                        .responseTimeMillis(0)
                        .build()));
    }

    @Test
    public void getUserDetailsShouldAddCachedHttpCallWhenCallCompletesSuccessful() throws JsonProcessingException {
        // given
        final UserDetailsResponse response = UserDetailsResponse.of(User.of(
                singletonList(
                        UserData.of("1", "rubicon", asList(Segment.of("2222"), Segment.of("3333")))),
                ExtUser.of(asList("L-1111", "O-2222"))));

        given(httpClient.post(anyString(), anyString(), anyLong())).willReturn(
                Future.succeededFuture(HttpClientResponse.of(200, null, jacksonMapper.encodeToString(response))));

        // when
        userService.getUserDetails(auctionContext, timeout).result();

        CacheHttpRequest.of("http://user-data.com",
                mapper.writeValueAsString(UserDetailsRequest.of(UTC_MILLIS_FORMATTER.format(now),
                        singletonList(UserId.of("khaos", "uid")))));

        // then
        assertThat(auctionContext.getDebugHttpCalls()).hasSize(1)
                .containsEntry("userservice", singletonList(DebugHttpCall.builder()
                        .requestUri("http://user-data.com")
                        .requestBody(mapper.writeValueAsString(UserDetailsRequest.of(UTC_MILLIS_FORMATTER.format(now),
                                singletonList(UserId.of("khaos", "uid")))))
                        .responseStatus(200)
                        .responseBody(mapper.writeValueAsString(
                                UserDetailsResponse.of(User.of(singletonList(UserData.of("1", "rubicon",
                                                asList(Segment.of("2222"), Segment.of("3333")))),
                                        ExtUser.of(asList("L-1111", "O-2222"))))))
                        .responseTimeMillis(0)
                        .build()));
    }

    @Test
    public void getUserDetailsShouldAddCachedHttpCallWhenCallFailed() throws JsonProcessingException {
        // given
        given(httpClient.post(anyString(), anyString(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(400, null, null)));

        // when
        userService.getUserDetails(auctionContext, timeout);

        // then
        assertThat(auctionContext.getDebugHttpCalls()).hasSize(1)
                .containsEntry("userservice", singletonList(DebugHttpCall.builder()
                        .requestUri("http://user-data.com")
                        .requestBody(mapper.writeValueAsString(UserDetailsRequest.of(UTC_MILLIS_FORMATTER.format(now),
                                singletonList(UserId.of("khaos", "uid")))))
                        .responseTimeMillis(0)
                        .build()));
    }

    @Test
    public void processWinEventShouldCallMetricsPreparationFailedMetricWhenHttpClientWhenMetaDataIsMissing() {
        // given
        given(lineItemService.getLineItemById(any())).willReturn(
                null,
                LineItem.of(LineItemMetaData.builder().build(), null, null, ZonedDateTime.now(clock)));

        // when
        userService.processWinEvent("lineItem1", "bidId", uidsCookie);

        // then
        verify(metrics).updateWinRequestPreparationFailed();
        verifyNoInteractions(httpClient);
    }

    @Test
    public void processWinEventShouldCallMetricsPreparationFailedMetricWhenHttpClientWhenUserIdsAreMissing() {
        // given
        final List<FrequencyCap> frequencyCaps = singletonList(FrequencyCap.builder().fcapId("213").build());
        final UidsCookie emptyCookie = new UidsCookie(Uids.builder().uids(emptyMap()).build(), jacksonMapper);

        given(lineItemService.getLineItemById(any())).willReturn(LineItem.of(
                LineItemMetaData.builder()
                        .source("rubicon")
                        .updatedTimeStamp(now)
                        .frequencyCaps(frequencyCaps)
                        .build(),
                null, null, ZonedDateTime.now(clock)));

        // when
        userService.processWinEvent("lineItem1", "bidId", emptyCookie);

        // then
        verify(metrics).updateWinRequestPreparationFailed();
        verifyNoInteractions(httpClient);
    }

    @Test
    public void processWinEventShouldCallMetricsWinRequestWithFalseWhenStatusIsNot200() {
        // given
        final List<FrequencyCap> frequencyCaps = singletonList(FrequencyCap.builder().fcapId("213").build());

        given(lineItemService.getLineItemById(any())).willReturn(LineItem.of(
                LineItemMetaData.builder()
                        .source("rubicon")
                        .updatedTimeStamp(now)
                        .frequencyCaps(frequencyCaps)
                        .build(),
                null, null, ZonedDateTime.now(clock)));

        given(httpClient.post(anyString(), anyString(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(400, null, null)));

        // when
        userService.processWinEvent("lineItem1", "bidId", uidsCookie);

        // then
        verify(metrics).updateWinEventRequestMetric(eq(false));
        verify(metrics).updateWinRequestTime(anyLong());
    }

    @Test
    public void processWinEventShouldCallMetricsWinRequestWithFalseWhenFailedFuture() {
        // given
        final List<FrequencyCap> frequencyCaps = singletonList(FrequencyCap.builder().fcapId("213").build());

        given(lineItemService.getLineItemById(any())).willReturn(LineItem.of(
                LineItemMetaData.builder()
                        .source("rubicon")
                        .updatedTimeStamp(now)
                        .frequencyCaps(frequencyCaps)
                        .build(),
                null, null, ZonedDateTime.now(clock)));

        given(httpClient.post(anyString(), anyString(), anyLong()))
                .willReturn(Future.failedFuture(new TimeoutException("timeout")));

        // when
        userService.processWinEvent("lineItem1", "bidId", uidsCookie);

        // then
        verify(metrics).updateWinEventRequestMetric(eq(false));
        verify(metrics).updateWinRequestTime(anyLong());
    }

    @Test
    public void processWinEventShouldCallExpectedServicesWithExpectedParameters() throws IOException {
        // given
        final List<FrequencyCap> frequencyCaps = singletonList(FrequencyCap.builder().fcapId("213").build());

        given(lineItemService.getLineItemById(any())).willReturn(LineItem.of(
                LineItemMetaData.builder()
                        .source("rubicon")
                        .updatedTimeStamp(now)
                        .frequencyCaps(frequencyCaps)
                        .build(),
                null, null, ZonedDateTime.now(clock)));

        given(httpClient.post(anyString(), anyString(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(200, null, null)));

        // when
        userService.processWinEvent("lineItem1", "bidId", uidsCookie);

        // then
        verify(lineItemService).getLineItemById(eq("lineItem1"));
        verify(metrics).updateWinNotificationMetric();
        verify(metrics).updateWinEventRequestMetric(eq(true));
        verify(metrics).updateWinRequestTime(anyLong());
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(httpClient).post(eq(WIN_EVENT_ENDPOINT), captor.capture(), eq(CONFIG_TIMEOUT));

        final WinEventNotification capturedRequest = mapper.readValue(captor.getValue(), WinEventNotification.class);
        assertThat(capturedRequest.getWinEventDateTime()).isEqualTo(UTC_MILLIS_FORMATTER.format(now));

        final WinEventNotification expectedRequestWithoutWinTime = WinEventNotification.builder()
                .bidderCode("rubicon")
                .bidId("bidId")
                .lineItemId("lineItem1")
                .region(DATA_CENTER_REGION)
                .userIds(singletonList(UserId.of("khaos", "uid")))
                .lineUpdatedDateTime(now)
                .frequencyCaps(frequencyCaps)
                .build();

        assertThat(capturedRequest)
                .usingRecursiveComparison()
                .ignoringFields("winEventDateTime")
                .isEqualTo(expectedRequestWithoutWinTime);
    }
}
