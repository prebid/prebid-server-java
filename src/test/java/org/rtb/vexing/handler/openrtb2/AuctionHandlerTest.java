package org.rtb.vexing.handler.openrtb2;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.BidResponse;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.rtb.vexing.VertxTest;
import org.rtb.vexing.auction.ExchangeService;
import org.rtb.vexing.auction.PreBidRequestContextFactory;
import org.rtb.vexing.auction.StoredRequestProcessor;
import org.rtb.vexing.config.ApplicationConfig;
import org.rtb.vexing.cookie.UidsCookieService;
import org.rtb.vexing.validation.RequestValidator;
import org.rtb.vexing.validation.ValidationResult;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

public class AuctionHandlerTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private ApplicationConfig config;
    @Mock
    private RequestValidator requestValidator;
    @Mock
    private ExchangeService exchangeService;
    @Mock
    private PreBidRequestContextFactory preBidRequestContextFactory;
    @Mock
    private UidsCookieService uidsCookieService;

    private AuctionHandler auctionHandler;

    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpRequest;
    @Mock
    private HttpServerResponse httpResponse;

    @Mock
    private StoredRequestProcessor storedRequestProcessor;

    @Before
    public void setUp() {
        given(config.getLong("max_request_size")).willReturn(Long.MAX_VALUE);

        given(routingContext.request()).willReturn(httpRequest);
        given(routingContext.response()).willReturn(httpResponse);
        given(httpResponse.putHeader(any(CharSequence.class), any(CharSequence.class))).willReturn(httpResponse);
        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);

        auctionHandler = AuctionHandler.create(config, requestValidator, exchangeService,
                storedRequestProcessor,
                preBidRequestContextFactory, uidsCookieService);
    }

    @Test
    public void createShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> AuctionHandler.create(null, null, null, null, null, null));
        assertThatNullPointerException().isThrownBy(() -> AuctionHandler.create(config, null, null, null, null, null));
        assertThatNullPointerException().isThrownBy(() -> AuctionHandler.create(config, requestValidator, null, null,
                null, null));
        assertThatNullPointerException().isThrownBy(() -> AuctionHandler.create(config, requestValidator,
                exchangeService, null, null, null));
        assertThatNullPointerException().isThrownBy(() -> AuctionHandler.create(config, requestValidator,
                exchangeService, storedRequestProcessor, null, null));
        assertThatNullPointerException().isThrownBy(() -> AuctionHandler.create(config, requestValidator,
                exchangeService, storedRequestProcessor, preBidRequestContextFactory, null));
    }

    @Test
    public void shouldRespondWithBadRequestIfRequestBodyIsMissing() {
        // given
        given(routingContext.getBody()).willReturn(null);

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Invalid request format: Incoming request has no body"));
    }

    @Test
    public void shouldRespondWithBadRequestIfRequestBodyExceedsMaxRequestSize() {
        // given
        given(config.getLong("max_request_size")).willReturn(1L);
        auctionHandler = AuctionHandler.create(config, requestValidator, exchangeService, storedRequestProcessor,
                preBidRequestContextFactory, uidsCookieService);


        given(routingContext.getBody()).willReturn(Buffer.buffer("body"));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Invalid request format: Request size exceeded max size of 1 bytes."));
    }

    @Test
    public void shouldRespondWithBadRequestIfRequestBodyCouldNotBeParsed() {
        // given
        given(routingContext.getBody()).willReturn(Buffer.buffer("body"));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(startsWith("Invalid request format: Failed to decode:"));
    }

    @Test
    public void shouldRespondWithBadRequestIfRequestIsNotValid() {
        // given
        given(routingContext.getBody()).willReturn(Buffer.buffer("{}"));

        given(requestValidator.validate(any())).willReturn(new ValidationResult(asList("error1", "error2")));

        given(storedRequestProcessor.processStoredRequests(any())).willReturn(Future.succeededFuture(
                BidRequest.builder().build()));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Invalid request format: error1\nInvalid request format: error2"));
    }

    @Test
    public void shouldRespondWithInternalServerErrorIfAuctionFails() {
        // given
        given(routingContext.getBody()).willReturn(Buffer.buffer("{}"));

        given(requestValidator.validate(any())).willReturn(new ValidationResult(emptyList()));

        given(storedRequestProcessor.processStoredRequests(any())).willReturn(Future
                .succeededFuture(BidRequest.builder().build()));

        given(exchangeService.holdAuction(any(), any())).willThrow(new RuntimeException("Unexpected exception"));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(500));
        verify(httpResponse).end(eq("Critical error while running the auction: Unexpected exception"));
    }

    @Test
    public void shouldRespondWithBidResponse() {
        // given
        given(routingContext.getBody()).willReturn(Buffer.buffer("{}"));

        given(preBidRequestContextFactory.fromRequest(any(), any())).willReturn(BidRequest.builder().build());

        given(requestValidator.validate(any())).willReturn(new ValidationResult(emptyList()));

        given(storedRequestProcessor.processStoredRequests(any())).willReturn(Future
                .succeededFuture(BidRequest.builder().build()));

        given(exchangeService.holdAuction(any(), any())).willReturn(
                Future.succeededFuture(BidResponse.builder().build()));

        // when
        auctionHandler.handle(routingContext);

        // then
        verify(exchangeService).holdAuction(any(), any());
        verify(httpResponse).putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
        verify(httpResponse).end(eq("{}"));
    }
}
