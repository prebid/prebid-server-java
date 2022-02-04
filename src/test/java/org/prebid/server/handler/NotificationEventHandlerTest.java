package org.prebid.server.handler;

import io.netty.util.AsciiString;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.analytics.AnalyticsReporterDelegator;
import org.prebid.server.analytics.model.NotificationEvent;
import org.prebid.server.auction.model.Tuple2;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.deals.UserService;
import org.prebid.server.deals.events.ApplicationEventService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountEventsConfig;
import org.prebid.server.util.ResourceUtil;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class NotificationEventHandlerTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private UidsCookieService uidsCookieService;
    @Mock
    private ApplicationEventService applicationEventService;
    @Mock
    private UserService userService;
    @Mock
    private AnalyticsReporterDelegator analyticsReporterDelegator;
    @Mock
    private TimeoutFactory timeoutFactory;
    @Mock
    private ApplicationSettings applicationSettings;

    private NotificationEventHandler notificationHandler;

    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpRequest;
    @Mock
    private HttpServerResponse httpResponse;

    @Before
    public void setUp() {
        given(routingContext.request()).willReturn(httpRequest);
        given(routingContext.response()).willReturn(httpResponse);

        given(httpRequest.headers()).willReturn(MultiMap.caseInsensitiveMultiMap());
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap());

        given(httpResponse.putHeader(any(CharSequence.class), any(CharSequence.class))).willReturn(httpResponse);
        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);

        notificationHandler = new NotificationEventHandler(
                uidsCookieService,
                applicationEventService,
                userService,
                analyticsReporterDelegator,
                timeoutFactory,
                applicationSettings,
                1000,
                true);
    }

    @Test
    public void shouldReturnBadRequestWhenTypeIsMissing() {
        // when
        notificationHandler.handle(routingContext);

        // then
        verifyNoInteractions(analyticsReporterDelegator);

        assertThat(captureResponseStatusCode()).isEqualTo(400);
    }

    @Test
    public void shouldReturnBadRequestWhenTypeIsInvalid() {
        // given
        given(httpRequest.params())
                .willReturn(MultiMap.caseInsensitiveMultiMap()
                        .add("t", "invalid"));

        // when
        notificationHandler.handle(routingContext);

        // then
        verifyNoInteractions(analyticsReporterDelegator);

        assertThat(captureResponseStatusCode()).isEqualTo(400);
    }

    @Test
    public void shouldReturnBadRequestWhenBidIdIsMissing() {
        // given
        given(httpRequest.params())
                .willReturn(MultiMap.caseInsensitiveMultiMap()
                        .add("t", "win"));

        // when
        notificationHandler.handle(routingContext);

        // then
        verifyNoInteractions(analyticsReporterDelegator);

        assertThat(captureResponseStatusCode()).isEqualTo(400);
    }

    @Test
    public void shouldReturnBadRequestWhenTimestampIsInvalid() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("t", "win")
                .add("b", "bidId")
                .add("bidder", "bidder")
                .add("ts", "invalid"));

        // when
        notificationHandler.handle(routingContext);

        // then
        verifyNoInteractions(analyticsReporterDelegator);

        assertThat(captureResponseStatusCode()).isEqualTo(400);
    }

    @Test
    public void shouldReturnUnauthorizedWhenAccountIsMissing() {
        // given
        given(httpRequest.params())
                .willReturn(MultiMap.caseInsensitiveMultiMap()
                        .add("t", "win")
                        .add("b", "bidId"));

        // when
        notificationHandler.handle(routingContext);

        // then
        verifyNoInteractions(analyticsReporterDelegator);

        assertThat(captureResponseStatusCode()).isEqualTo(401);
    }

    @Test
    public void shouldReturnBadRequestWhenFormatValueIsInvalid() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("t", "win")
                .add("b", "bidId")
                .add("a", "accountId")
                .add("f", "invalid"));

        // when
        notificationHandler.handle(routingContext);

        // then
        verifyNoInteractions(analyticsReporterDelegator);

        assertThat(captureResponseStatusCode()).isEqualTo(400);
    }

    @Test
    public void shouldReturnBadRequestWhenAnalyticsValueIsInvalid() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("t", "win")
                .add("b", "bidId")
                .add("a", "accountId")
                .add("f", "b")
                .add("x", "invalid"));

        // when
        notificationHandler.handle(routingContext);

        // then
        verifyNoInteractions(analyticsReporterDelegator);

        assertThat(captureResponseStatusCode()).isEqualTo(400);
    }

    @Test
    public void shouldReturnBadRequestWhenIntegrationValueIsInvalid() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("t", "win")
                .add("b", "bidId")
                .add("a", "accountId")
                .add("f", "b")
                .add("x", "invalid")
                .add("int", "pbjs+="));

        // when
        notificationHandler.handle(routingContext);

        // then
        verifyNoInteractions(analyticsReporterDelegator);

        assertThat(captureResponseStatusCode()).isEqualTo(400);
    }

    @Test
    public void shouldNotPassEventToAnalyticsReporterWhenAccountNotFound() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("t", "win")
                .add("b", "bidId")
                .add("a", "accountId"));

        given(applicationSettings.getAccountById(anyString(), any()))
                .willReturn(Future.failedFuture(new PreBidException("Not Found")));

        // when
        notificationHandler.handle(routingContext);

        // then
        verifyNoInteractions(analyticsReporterDelegator);

        assertThat(captureResponseStatusCode()).isEqualTo(401);
        assertThat(captureResponseBody()).isEqualTo("Account 'accountId' doesn't support events");
    }

    @Test
    public void shouldNotPassEventToAnalyticsReporterWhenAccountEventNotEnabled() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("t", "win")
                .add("b", "bidId")
                .add("a", "accountId"));

        given(applicationSettings.getAccountById(anyString(), any()))
                .willReturn(Future.succeededFuture(Account.builder()
                        .id("accountId")
                        .auction(AccountAuctionConfig.builder()
                                .events(AccountEventsConfig.of(false))
                                .build())
                        .build()));

        // when
        notificationHandler.handle(routingContext);

        // then
        verifyNoInteractions(analyticsReporterDelegator);

        assertThat(captureResponseStatusCode()).isEqualTo(401);
        assertThat(captureResponseBody()).isEqualTo("Account 'accountId' doesn't support events");
    }

    @Test
    public void shouldPassEventToAnalyticsReporterWhenAccountEventEnabled() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("t", "win")
                .add("b", "bidId")
                .add("a", "accountId"));

        final Account account = Account.builder()
                .auction(AccountAuctionConfig.builder()
                        .events(AccountEventsConfig.of(true))
                        .build())
                .build();
        given(applicationSettings.getAccountById(anyString(), any()))
                .willReturn(Future.succeededFuture(account));

        // when
        notificationHandler.handle(routingContext);

        // then
        final CaseInsensitiveMultiMap.Builder queryParams = CaseInsensitiveMultiMap.builder()
                .add("t", "win")
                .add("b", "bidId")
                .add("a", "accountId");
        final HttpRequestContext expectedHttpContext = HttpRequestContext.builder()
                .queryParams(queryParams.build())
                .headers(CaseInsensitiveMultiMap.empty())
                .build();

        assertThat(captureAnalyticEvent()).isEqualTo(NotificationEvent.builder()
                .type(NotificationEvent.Type.win)
                .bidId("bidId")
                .account(account)
                .httpContext(expectedHttpContext)
                .build());
    }

    @Test
    public void shouldUpdateEventForLineItemForEventTypeWinAndAccountEventsEnabled() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("t", "win")
                .add("b", "bidId")
                .add("l", "lineItemId")
                .add("a", "accountId"));

        final Account account = Account.builder()
                .auction(AccountAuctionConfig.builder()
                        .events(AccountEventsConfig.of(true))
                        .build())
                .build();
        given(applicationSettings.getAccountById(anyString(), any()))
                .willReturn(Future.succeededFuture(account));

        // when
        notificationHandler.handle(routingContext);

        // then
        verify(applicationEventService).publishLineItemWinEvent(eq("lineItemId"));
    }

    @Test
    public void shouldProcessLineItemEventWhenRequestAnalyticsFlagDisabled() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("t", "win")
                .add("b", "bidId")
                .add("l", "lineItemId")
                .add("a", "accountId")
                .add("x", "0"));

        final Account account = Account.builder()
                .auction(AccountAuctionConfig.builder()
                        .events(AccountEventsConfig.of(true))
                        .build())
                .build();
        given(applicationSettings.getAccountById(anyString(), any()))
                .willReturn(Future.succeededFuture(account));

        // when
        notificationHandler.handle(routingContext);

        // then
        verify(applicationEventService).publishLineItemWinEvent(eq("lineItemId"));
        verify(userService).processWinEvent(eq("lineItemId"), eq("bidId"), any());
    }

    @Test
    public void shouldProcessLineItemEventWhenAccountEventsDisabled() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("t", "win")
                .add("b", "bidId")
                .add("l", "lineItemId")
                .add("a", "accountId"));

        final Account account = Account.builder()
                .auction(AccountAuctionConfig.builder()
                        .events(AccountEventsConfig.of(false))
                        .build())
                .build();
        given(applicationSettings.getAccountById(anyString(), any()))
                .willReturn(Future.succeededFuture(account));

        // when
        notificationHandler.handle(routingContext);

        // then
        verify(applicationEventService).publishLineItemWinEvent(eq("lineItemId"));
        verify(userService).processWinEvent(eq("lineItemId"), eq("bidId"), any());
    }

    @Test
    public void shouldNotProcessLineItemEventWhenDealsDisabled() {
        // given
        notificationHandler = new NotificationEventHandler(
                uidsCookieService,
                applicationEventService,
                userService,
                analyticsReporterDelegator,
                timeoutFactory,
                applicationSettings,
                1000,
                false);

        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("t", "win")
                .add("b", "bidId")
                .add("l", "lineItemId")
                .add("a", "accountId"));

        final Account account = Account.builder()
                .auction(AccountAuctionConfig.builder()
                        .events(AccountEventsConfig.of(true))
                        .build())
                .build();
        given(applicationSettings.getAccountById(anyString(), any()))
                .willReturn(Future.succeededFuture(account));

        // when
        notificationHandler.handle(routingContext);

        // then
        verifyNoInteractions(applicationEventService);
        verifyNoInteractions(userService);
    }

    @Test
    public void shouldNotPassEventToAnalyticsReporterWhenAnalyticsValueIsZero() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("t", "win")
                .add("b", "bidId")
                .add("a", "accountId")
                .add("x", "0"));

        given(applicationSettings.getAccountById(anyString(), any()))
                .willReturn(Future.succeededFuture(Account.builder()
                        .auction(AccountAuctionConfig.builder()
                                .events(AccountEventsConfig.of(true))
                                .build())
                        .build()));

        // when
        notificationHandler.handle(routingContext);

        // then
        verifyNoInteractions(analyticsReporterDelegator);
    }

    @Test
    public void shouldRespondWhenAnalyticsValueIsZeroAndDoNotSetStatusManually() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("t", "win")
                .add("b", "bidId")
                .add("a", "accountId")
                .add("x", "0"));

        given(applicationSettings.getAccountById(anyString(), any()))
                .willReturn(Future.succeededFuture(Account.builder()
                        .auction(AccountAuctionConfig.builder()
                                .events(AccountEventsConfig.of(true))
                                .build())
                        .build()));

        // when
        notificationHandler.handle(routingContext);

        // then
        verify(httpResponse).end();
    }

    @Test
    public void shouldRespondWithPixelAndContentTypeWhenRequestFormatIsImp() throws IOException {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("t", "win")
                .add("b", "bidId")
                .add("a", "accountId")
                .add("f", "i"));

        given(applicationSettings.getAccountById(anyString(), any()))
                .willReturn(Future.succeededFuture(Account.builder()
                        .auction(AccountAuctionConfig.builder()
                                .events(AccountEventsConfig.of(true))
                                .build())
                        .build()));

        // when
        notificationHandler.handle(routingContext);

        // then
        final Tuple2<CharSequence, CharSequence> header = captureHeader();
        assertThat(header.getLeft()).isEqualTo(AsciiString.of("content-type"));
        assertThat(header.getRight()).isEqualTo("image/png");
        assertThat(captureResponseBodyBuffer())
                .isEqualTo(Buffer.buffer(ResourceUtil.readByteArrayFromClassPath("static/tracking-pixel.png")));
    }

    @Test
    public void shouldRespondWithNoContentWhenRequestFormatIsNotDefined() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("t", "win")
                .add("b", "bidId")
                .add("a", "accountId"));

        given(applicationSettings.getAccountById(anyString(), any()))
                .willReturn(Future.succeededFuture(Account.builder()
                        .auction(AccountAuctionConfig.builder()
                                .events(AccountEventsConfig.of(true))
                                .build())
                        .build()));

        // when
        notificationHandler.handle(routingContext);

        // then
        verify(httpResponse).end();
    }

    @Test
    public void shouldPassExpectedEventToAnalyticsReporter() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("t", "win")
                .add("b", "bidId")
                .add("a", "accountId")
                .add("bidder", "bidder")
                .add("ts", "1000")
                .add("int", "pbjs"));

        final Account account = Account.builder()
                .auction(AccountAuctionConfig.builder()
                        .events(AccountEventsConfig.of(true))
                        .build())
                .build();
        given(applicationSettings.getAccountById(anyString(), any()))
                .willReturn(Future.succeededFuture(account));

        // when
        notificationHandler.handle(routingContext);

        // then
        final CaseInsensitiveMultiMap.Builder queryParams = CaseInsensitiveMultiMap.builder()
                .add("t", "win")
                .add("b", "bidId")
                .add("a", "accountId")
                .add("bidder", "bidder")
                .add("ts", "1000")
                .add("int", "pbjs");
        final HttpRequestContext expectedHttpContext = HttpRequestContext.builder()
                .queryParams(queryParams.build())
                .headers(CaseInsensitiveMultiMap.empty())
                .build();

        assertThat(captureAnalyticEvent()).isEqualTo(NotificationEvent.builder()
                .type(NotificationEvent.Type.win)
                .bidId("bidId")
                .account(account)
                .bidder("bidder")
                .timestamp(1000L)
                .integration("pbjs")
                .httpContext(expectedHttpContext)
                .build());
    }

    @Test
    public void shouldPassEventObjectToUserServiceWhenLineItemIdParameterIsPresent() {
        // given
        given(httpRequest.params())
                .willReturn(MultiMap.caseInsensitiveMultiMap()
                        .add("t", "win")
                        .add("b", "bidId")
                        .add("a", "accountId")
                        .add("l", "lineItemId"));

        given(applicationSettings.getAccountById(anyString(), any()))
                .willReturn(Future.succeededFuture(Account.builder()
                        .id("accountId")
                        .auction(AccountAuctionConfig.builder()
                                .events(AccountEventsConfig.of(true))
                                .build())
                        .build()));

        // when
        notificationHandler.handle(routingContext);

        // then
        verify(uidsCookieService).parseFromRequest(eq(routingContext));

        final CaseInsensitiveMultiMap.Builder queryParams = CaseInsensitiveMultiMap.builder()
                .add("t", "win")
                .add("b", "bidId")
                .add("a", "accountId")
                .add("l", "lineItemId");

        final HttpRequestContext expectedHttpContext = HttpRequestContext.builder()
                .queryParams(queryParams.build())
                .headers(CaseInsensitiveMultiMap.empty())
                .build();
        final NotificationEvent expectedEvent = NotificationEvent.builder()
                .type(NotificationEvent.Type.win)
                .bidId("bidId")
                .account(Account.builder()
                        .id("accountId")
                        .auction(AccountAuctionConfig.builder()
                                .events(AccountEventsConfig.of(true))
                                .build())
                        .build())
                .httpContext(expectedHttpContext)
                .lineItemId("lineItemId")
                .build();

        verify(userService).processWinEvent(eq("lineItemId"), eq("bidId"), isNull());
        verify(analyticsReporterDelegator).processEvent(eq(expectedEvent));
    }

    private Integer captureResponseStatusCode() {
        final ArgumentCaptor<Integer> captor = ArgumentCaptor.forClass(Integer.class);
        verify(httpResponse).setStatusCode(captor.capture());
        return captor.getValue();
    }

    private Buffer captureResponseBodyBuffer() {
        final ArgumentCaptor<Buffer> captor = ArgumentCaptor.forClass(Buffer.class);
        verify(httpResponse).end(captor.capture());
        return captor.getValue();
    }

    private String captureResponseBody() {
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(httpResponse).end(captor.capture());
        return captor.getValue();
    }

    private Tuple2<CharSequence, CharSequence> captureHeader() {
        final ArgumentCaptor<CharSequence> headerNameCaptor = ArgumentCaptor.forClass(CharSequence.class);
        final ArgumentCaptor<CharSequence> headerValueCaptor = ArgumentCaptor.forClass(CharSequence.class);
        verify(httpResponse).putHeader(headerNameCaptor.capture(), headerValueCaptor.capture());
        return Tuple2.of(headerNameCaptor.getValue(), headerValueCaptor.getValue());
    }

    private NotificationEvent captureAnalyticEvent() {
        final ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(analyticsReporterDelegator).processEvent(captor.capture());
        return captor.getValue();
    }
}
