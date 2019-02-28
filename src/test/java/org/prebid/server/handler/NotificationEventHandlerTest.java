package org.prebid.server.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.util.AsciiString;
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
import org.prebid.server.analytics.model.NotificationEvent;
import org.prebid.server.auction.model.Tuple2;
import org.prebid.server.proto.request.EventNotificationRequest;
import org.prebid.server.util.ResourceUtil;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

public class NotificationEventHandlerTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private RoutingContext routingContext;

    @Mock
    private HttpServerRequest httpRequest;

    @Mock
    private HttpServerResponse httpResponse;

    @Mock
    private AnalyticsReporter analyticsReporter;

    private NotificationEventHandler notificationHandler;

    @Before
    public void setUp() {
        given(routingContext.request()).willReturn(httpRequest);
        given(routingContext.response()).willReturn(httpResponse);
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap());
        given(httpRequest.headers()).willReturn(new CaseInsensitiveHeaders());
        given(httpResponse.putHeader(any(CharSequence.class), any(CharSequence.class))).willReturn(httpResponse);
        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);
        notificationHandler = NotificationEventHandler.create(analyticsReporter);
    }

    @Test
    public void shouldThrowIllegalExceptionWhenReporterIsNull() {
        // given, when and then
        assertThatNullPointerException().isThrownBy(() -> NotificationEventHandler.create(null));
    }

    @Test
    public void shouldReturnBadRequestWhenRequestBodyIsNull() {
        // when
        notificationHandler.handle(routingContext);
        // then
        assertThat(captureResponseStatusCode()).isEqualTo(400);
        assertThat(captureResponseBody()).isEqualTo("Request is invalid: Request body was empty." +
                " Expected request with body has next fields: type, bidid and bidder.");
    }

    @Test
    public void shouldReturnBadRequestWhenBodyCantBeParsedToEventRequest() {
        // given
        given(routingContext.getBody()).willReturn(Buffer.buffer("{"));
        // when
        notificationHandler.handle(routingContext);
        // then
        assertThat(captureResponseStatusCode()).isEqualTo(400);
        assertThat(captureResponseBody()).isEqualTo("Request is invalid: Request body couldn't be parsed." +
                " Expected request body has next fields: type, bidid and bidder.");
    }

    @Test
    public void shouldReturnBadRequestWhenTypeIsNull() {
        // given
        given(routingContext.getBody()).willReturn(Buffer.buffer("{}"));
        // when
        notificationHandler.handle(routingContext);
        // then
        assertThat(captureResponseStatusCode()).isEqualTo(400);
        assertThat(captureResponseBody())
                .isEqualTo("Request is invalid: Type is required parameter. Possible values are win and view, but was null");
    }

    @Test
    public void shouldReturnBadRequestWhenTypeIsNotViewOrWin() throws JsonProcessingException {
        // given
        given(routingContext.getBody()).willReturn(Buffer.buffer(
                mapper.writeValueAsString(EventNotificationRequest.builder().type("invalid").build())));
        // when
        notificationHandler.handle(routingContext);
        // then
        assertThat(captureResponseStatusCode()).isEqualTo(400);
        assertThat(captureResponseBody())
                .isEqualTo("Request is invalid: Type is required parameter. Possible values are win and view, but was invalid");
    }

    @Test
    public void shouldReturnBadRequestWhenBididWasNotDefined() throws JsonProcessingException {
        // given
        given(routingContext.getBody()).willReturn(Buffer.buffer(
                mapper.writeValueAsString(EventNotificationRequest.builder().type("win").build())));
        // when
        notificationHandler.handle(routingContext);
        // then
        assertThat(captureResponseStatusCode()).isEqualTo(400);
        assertThat(captureResponseBody())
                .isEqualTo("Request is invalid: bidid is required and can't be empty.");
    }

    @Test
    public void shouldReturnBadRequestWhenBidderWasNotDefined() throws JsonProcessingException {
        // given
        given(routingContext.getBody()).willReturn(Buffer.buffer(
                mapper.writeValueAsString(EventNotificationRequest.builder().type("win").bidId("bidId").build())));
        // when
        notificationHandler.handle(routingContext);
        // then
        assertThat(captureResponseStatusCode()).isEqualTo(400);
        assertThat(captureResponseBody())
                .isEqualTo("Request is invalid: bidder is required and can't be empty.");
    }

    @Test
    public void shouldPassEventObjectToAnalyticReporter() throws JsonProcessingException {
        // given
        given(routingContext.getBody()).willReturn(Buffer.buffer(
                mapper.writeValueAsString(EventNotificationRequest.builder().type("win").bidId("bidId").bidder("rubicon").build())));

        // when
        notificationHandler.handle(routingContext);

        // then
        assertThat(captureAnalyticEvent()).isEqualTo(new NotificationEvent("win", "bidId", "rubicon"));
    }

    @Test
    public void shouldRespondWithBadRequestWhenFormatParameterIsNotJPGOrPNG() throws JsonProcessingException {
        // given
        given(routingContext.getBody()).willReturn(Buffer.buffer(
                mapper.writeValueAsString(EventNotificationRequest.builder().type("win").bidId("bidId").bidder("rubicon").build())));
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap().add("format", "invalid"));

        // when
        notificationHandler.handle(routingContext);

        // then
        assertThat(captureResponseStatusCode()).isEqualTo(400);
        assertThat(captureResponseBody())
                .isEqualTo("Request is invalid: format when defined should has value of png or jpg.");
    }

    @Test
    public void shouldRespondWithPixelTrackingPngByteAndContentTypePngHeader() throws IOException {
        // given
        given(routingContext.getBody()).willReturn(Buffer.buffer(
                mapper.writeValueAsString(EventNotificationRequest.builder().type("win").bidId("bidId").bidder("rubicon").build())));
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap().add("format", "png"));

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
    public void shouldRespondWithPixelTrackingJpgByteAndContentTypeJpgHeader() throws IOException {
        // given
        given(routingContext.getBody()).willReturn(Buffer.buffer(
                mapper.writeValueAsString(EventNotificationRequest.builder().type("win").bidId("bidId").bidder("rubicon").build())));
        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap().add("format", "jpg"));

        // when
        notificationHandler.handle(routingContext);

        // then
        final Tuple2<CharSequence, CharSequence> header = captureHeader();
        assertThat(header.getLeft()).isEqualTo(AsciiString.of("content-type"));
        assertThat(header.getRight()).isEqualTo("image/jpeg");
        assertThat(captureResponseBodyBuffer())
                .isEqualTo(Buffer.buffer(ResourceUtil.readByteArrayFromClassPath("static/tracking-pixel.jpg")));
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
