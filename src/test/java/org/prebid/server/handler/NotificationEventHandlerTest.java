package org.prebid.server.handler;

import io.netty.util.AsciiString;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.CaseInsensitiveHeaders;
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
import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.model.HttpContext;
import org.prebid.server.analytics.model.NotificationEvent;
import org.prebid.server.auction.model.Tuple2;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.Account;
import org.prebid.server.util.ResourceUtil;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

public class NotificationEventHandlerTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private AnalyticsReporter analyticsReporter;
    @Mock
    private TimeoutFactory timeoutFactory;
    @Mock
    private ApplicationSettings applicationSettings;
    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpRequest;
    @Mock
    private HttpServerResponse httpResponse;

    private NotificationEventHandler notificationHandler;

    @Before
    public void setUp() {
        given(routingContext.request()).willReturn(httpRequest);
        given(routingContext.response()).willReturn(httpResponse);

        given(httpRequest.headers()).willReturn(new CaseInsensitiveHeaders());
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap());

        given(httpResponse.putHeader(any(CharSequence.class), any(CharSequence.class))).willReturn(httpResponse);
        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);

        notificationHandler = new NotificationEventHandler(analyticsReporter, timeoutFactory, applicationSettings);
    }

    @Test
    public void shouldReturnBadRequestWhenTypeIsMissing() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap());

        // when
        notificationHandler.handle(routingContext);

        // then
        verifyZeroInteractions(analyticsReporter);

        assertThat(captureResponseStatusCode()).isEqualTo(400);
    }

    @Test
    public void shouldReturnBadRequestWhenTypeIsInvalid() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("t", "invalid"));

        // when
        notificationHandler.handle(routingContext);

        // then
        verifyZeroInteractions(analyticsReporter);

        assertThat(captureResponseStatusCode()).isEqualTo(400);
    }

    @Test
    public void shouldReturnBadRequestWhenBidIdIsMissing() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("t", "win"));

        // when
        notificationHandler.handle(routingContext);

        // then
        verifyZeroInteractions(analyticsReporter);

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
        verifyZeroInteractions(analyticsReporter);

        assertThat(captureResponseStatusCode()).isEqualTo(400);
    }

    @Test
    public void shouldReturnUnauthorizedWhenAccountIsMissing() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("t", "win")
                .add("b", "bidId"));

        // when
        notificationHandler.handle(routingContext);

        // then
        verifyZeroInteractions(analyticsReporter);

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
        verifyZeroInteractions(analyticsReporter);

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
        verifyZeroInteractions(analyticsReporter);

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
        verifyZeroInteractions(analyticsReporter);

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
                .willReturn(Future.succeededFuture(Account.builder().id("accountId").eventsEnabled(false).build()));

        // when
        notificationHandler.handle(routingContext);

        // then
        verifyZeroInteractions(analyticsReporter);

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

        final Account account = Account.builder().eventsEnabled(true).build();
        given(applicationSettings.getAccountById(anyString(), any()))
                .willReturn(Future.succeededFuture(account));

        // when
        notificationHandler.handle(routingContext);

        // then
        final Map<String, String> queryParams = new HashMap<>();
        queryParams.put("t", "win");
        queryParams.put("b", "bidId");
        queryParams.put("a", "accountId");
        final HttpContext expectedHttpContext = HttpContext.builder()
                .queryParams(queryParams)
                .headers(Collections.emptyMap())
                .cookies(Collections.emptyMap())
                .build();

        assertThat(captureAnalyticEvent()).isEqualTo(NotificationEvent.builder()
                .type(NotificationEvent.Type.win)
                .bidId("bidId")
                .account(account)
                .httpContext(expectedHttpContext)
                .build());
    }

    @Test
    public void shouldNotPassEventToAnalyticsReporterWhenAnalyticsValueIsZero() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("t", "win")
                .add("b", "bidId")
                .add("a", "accountId")
                .add("x", "0"));

        // when
        notificationHandler.handle(routingContext);

        // then
        verifyZeroInteractions(applicationSettings);
        verifyZeroInteractions(analyticsReporter);
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
                .willReturn(Future.succeededFuture(Account.builder().eventsEnabled(true).build()));

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
                .willReturn(Future.succeededFuture(Account.builder().eventsEnabled(true).build()));

        // when
        notificationHandler.handle(routingContext);

        // then
        verify(httpResponse).end();
        verifyNoMoreInteractions(httpResponse);
    }

    @Test
    public void shouldPassExpectedEventToAnalyticsReporter() {
        // given
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("t", "win")
                .add("b", "bidId")
                .add("a", "accountId")
                .add("bidder", "bidder")
                .add("ts", "1000"));

        final Account account = Account.builder().eventsEnabled(true).build();
        given(applicationSettings.getAccountById(anyString(), any()))
                .willReturn(Future.succeededFuture(account));

        // when
        notificationHandler.handle(routingContext);

        // then
        final Map<String, String> queryParams = new HashMap<>();
        queryParams.put("t", "win");
        queryParams.put("b", "bidId");
        queryParams.put("a", "accountId");
        queryParams.put("bidder", "bidder");
        queryParams.put("ts", "1000");
        final HttpContext expectedHttpContext = HttpContext.builder()
                .queryParams(queryParams)
                .headers(Collections.emptyMap())
                .cookies(Collections.emptyMap())
                .build();

        assertThat(captureAnalyticEvent()).isEqualTo(NotificationEvent.builder()
                .type(NotificationEvent.Type.win)
                .bidId("bidId")
                .account(account)
                .bidder("bidder")
                .timestamp(1000L)
                .httpContext(expectedHttpContext)
                .build());
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
        verify(analyticsReporter).processEvent(captor.capture());
        return captor.getValue();
    }
}
