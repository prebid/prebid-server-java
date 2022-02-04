package org.prebid.server.log;

import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.logging.Logger;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.log.model.HttpLogSpec;
import org.prebid.server.metric.MetricName;
import org.prebid.server.settings.model.Account;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class HttpInteractionLoggerTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Logger logger;
    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest serverRequest;

    private HttpInteractionLogger testingInstance;

    @Before
    public void setUp() {
        testingInstance = new HttpInteractionLogger(jacksonMapper);
        given(routingContext.getBodyAsString()).willReturn("{}");
        given(routingContext.request()).willReturn(serverRequest);
        given(serverRequest.uri()).willReturn("example.com");
        ReflectionTestUtils.setField(testingInstance, "logger", logger);
    }

    @Test
    public void maybeLogOpenrtb2AuctionShouldLogWithExpectedParams() {
        // given
        final AuctionContext givenAuctionContext =
                givenAuctionContext(accountBuilder -> accountBuilder.id("123"));
        final HttpLogSpec givenSpec = HttpLogSpec.of(null, null, "123", null, 1);

        // when
        testingInstance.setSpec(givenSpec);
        testingInstance.maybeLogOpenrtb2Auction(givenAuctionContext, routingContext, 200, "responseBody");

        // then
        verify(logger)
                .info("Requested URL: \"{0}\", request body: \"{1}\", response status: \"{2}\", response body: \"{3}\"",
                        "example.com",
                        "{}",
                        200,
                        "responseBody");
    }

    @Test
    public void maybeLogOpenrtb2AuctionShouldLimitLogBySpecLimit() {
        // given
        final AuctionContext givenAuctionContext =
                givenAuctionContext(accountBuilder -> accountBuilder.id("123"));
        final HttpLogSpec givenSpec = HttpLogSpec.of(null, null, "123", null, 1);

        // when
        testingInstance.setSpec(givenSpec);
        testingInstance.maybeLogOpenrtb2Auction(givenAuctionContext, routingContext, 200, null);
        testingInstance.maybeLogOpenrtb2Auction(givenAuctionContext, routingContext, 200, null);

        // then
        verify(logger).info(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    public void maybeLogOpenrtb2AuctionShouldNotLogIfAccountIdNotEqualsToGivenInSpec() {
        // given
        final AuctionContext givenAuctionContext =
                givenAuctionContext(accountBuilder -> accountBuilder.id("456"));
        final HttpLogSpec givenSpec = HttpLogSpec.of(null, null, "123", null, 1);

        // when
        testingInstance.setSpec(givenSpec);
        testingInstance.maybeLogOpenrtb2Auction(givenAuctionContext, routingContext, 200, null);

        // then
        verifyNoInteractions(logger);
    }

    @Test
    public void maybeLogOpenrtb2AuctionShouldLogIfStatusEqualsToGivenInSpec() {
        // given
        final AuctionContext givenAuctionContext = givenAuctionContext(identity());
        final HttpLogSpec givenSpec = HttpLogSpec.of(null, 501, null, null, 1);

        // when
        testingInstance.setSpec(givenSpec);
        testingInstance.maybeLogOpenrtb2Auction(givenAuctionContext, routingContext, 200, null);
        testingInstance.maybeLogOpenrtb2Auction(givenAuctionContext, routingContext, 501, null);

        // then
        verify(logger).info(anyString(), anyString(), anyString(), eq(501), any());
        verify(logger, never()).info(anyString(), anyString(), anyString(), eq(200), any());
    }

    @Test
    public void maybeLogOpenrtb2AuctionShouldLogIfSpecEndpointIsAuction() {
        // given
        final AuctionContext givenAuctionContext = givenAuctionContext(identity());
        final HttpLogSpec givenSpec = HttpLogSpec.of(HttpLogSpec.Endpoint.auction, null, null, null, 1);

        // when
        testingInstance.setSpec(givenSpec);
        testingInstance.maybeLogOpenrtb2Auction(givenAuctionContext, routingContext, 200, null);

        // then
        verify(logger).info(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    public void maybeLogOpenrtb2AuctionShouldNotLogIfSpecEndpointIsNotAuction() {
        // given
        final AuctionContext givenAuctionContext = givenAuctionContext(identity());
        final HttpLogSpec givenSpec = HttpLogSpec.of(HttpLogSpec.Endpoint.amp, null, null, null, 1);

        // when
        testingInstance.setSpec(givenSpec);
        testingInstance.maybeLogOpenrtb2Auction(givenAuctionContext, routingContext, 200, null);

        // then
        verifyNoInteractions(logger);
    }

    @Test
    public void maybeLogOpenrtb2AuctionShouldLogOneLineBodyFromContext() {
        // given
        given(routingContext.getBodyAsString()).willReturn("{\n"
                + "  \"param\": \"value\"\n"
                + "}");
        final AuctionContext givenAuctionContext =
                givenAuctionContext(accountBuilder -> accountBuilder.id("123"));
        final HttpLogSpec givenSpec = HttpLogSpec.of(null, null, "123", null, 1);

        // when
        testingInstance.setSpec(givenSpec);
        testingInstance.maybeLogOpenrtb2Auction(givenAuctionContext, routingContext, 200, null);

        // then
        verify(logger).info(anyString(), anyString(), eq("{\"param\":\"value\"}"), any(), any());
    }

    @Test
    public void maybeLogOpenrtb2AuctionShouldLogMessageInsteadOfInvalidBody() {
        // given
        given(routingContext.getBodyAsString()).willReturn("{");
        final AuctionContext givenAuctionContext =
                givenAuctionContext(accountBuilder -> accountBuilder.id("123"));
        final HttpLogSpec givenSpec = HttpLogSpec.of(null, null, "123", null, 1);

        // when
        testingInstance.setSpec(givenSpec);
        testingInstance.maybeLogOpenrtb2Auction(givenAuctionContext, routingContext, 200, null);

        // then
        verify(logger).info(anyString(), anyString(), eq("Not parseable JSON passed: {"), any(), any());
    }

    @Test
    public void maybeLogOpenrtb2AmpShouldLogWithExpectedParams() {
        // given
        final AuctionContext givenAuctionContext =
                givenAuctionContext(accountBuilder -> accountBuilder.id("123"));
        final HttpLogSpec givenSpec = HttpLogSpec.of(null, null, "123", null, 1);

        // when
        testingInstance.setSpec(givenSpec);
        testingInstance.maybeLogOpenrtb2Amp(givenAuctionContext, routingContext, 200, "responseBody");

        // then
        verify(logger)
                .info("Requested URL: \"{0}\", response status: \"{1}\", response body: \"{2}\"",
                        "example.com",
                        200,
                        "responseBody");
    }

    @Test
    public void maybeLogOpenrtb2AmpShouldLimitLogBySpecLimit() {
        // given
        final AuctionContext givenAuctionContext =
                givenAuctionContext(accountBuilder -> accountBuilder.id("123"));
        final HttpLogSpec givenSpec = HttpLogSpec.of(null, null, "123", null, 1);

        // when
        testingInstance.setSpec(givenSpec);
        testingInstance.maybeLogOpenrtb2Amp(givenAuctionContext, routingContext, 200, null);
        testingInstance.maybeLogOpenrtb2Amp(givenAuctionContext, routingContext, 200, null);

        // then
        verify(logger).info(anyString(), anyString(), any(), any());
    }

    @Test
    public void maybeLogOpenrtb2AmpShouldNotLogIfAccountIdNotEqualsToGivenInSpec() {
        // given
        final AuctionContext givenAuctionContext =
                givenAuctionContext(accountBuilder -> accountBuilder.id("456"));
        final HttpLogSpec givenSpec = HttpLogSpec.of(null, null, "123", null, 1);

        // when
        testingInstance.setSpec(givenSpec);
        testingInstance.maybeLogOpenrtb2Amp(givenAuctionContext, routingContext, 200, null);

        // then
        verifyNoInteractions(logger);
    }

    @Test
    public void maybeLogOpenrtb2AmpShouldLogIfStatusEqualsToGivenInSpec() {
        // given
        final AuctionContext givenAuctionContext = givenAuctionContext(identity());
        final HttpLogSpec givenSpec = HttpLogSpec.of(null, 501, null, null, 1);

        // when
        testingInstance.setSpec(givenSpec);
        testingInstance.maybeLogOpenrtb2Amp(givenAuctionContext, routingContext, 200, null);
        testingInstance.maybeLogOpenrtb2Amp(givenAuctionContext, routingContext, 501, null);

        // then
        verify(logger).info(anyString(), anyString(), eq(501), any());
        verify(logger, never()).info(anyString(), anyString(), eq(200), any());
    }

    @Test
    public void maybeLogOpenrtb2AmpShouldLogIfSpecEndpointIsAmp() {
        // given
        final AuctionContext givenAuctionContext = givenAuctionContext(identity());
        final HttpLogSpec givenSpec = HttpLogSpec.of(HttpLogSpec.Endpoint.amp, null, null, null, 1);

        // when
        testingInstance.setSpec(givenSpec);
        testingInstance.maybeLogOpenrtb2Amp(givenAuctionContext, routingContext, 200, null);

        // then
        verify(logger).info(anyString(), anyString(), any(), any());
    }

    @Test
    public void maybeLogOpenrtb2AmpShouldNotLogIfSpecEndpointIsNotAmp() {
        // given
        final AuctionContext givenAuctionContext = givenAuctionContext(identity());
        final HttpLogSpec givenSpec = HttpLogSpec.of(HttpLogSpec.Endpoint.auction, null, null, null, 1);

        // when
        testingInstance.setSpec(givenSpec);
        testingInstance.maybeLogOpenrtb2Amp(givenAuctionContext, routingContext, 200, null);

        // then
        verifyNoInteractions(logger);
    }

    @Test
    public void maybeLogBidderRequestShouldLogWithExpectedParams() {
        // given
        final AuctionContext givenAuctionContext =
                givenAuctionContext(accountBuilder -> accountBuilder.id("123"));
        final BidderRequest givenBidderRequest = givenBidderRequest(identity());
        final HttpLogSpec givenSpec = HttpLogSpec.of(null, null, "123", "bidderName", 1);

        // when
        testingInstance.setSpec(givenSpec);
        testingInstance.maybeLogBidderRequest(givenAuctionContext, givenBidderRequest);

        // then
        verify(logger).info("Request body to {0}: \"{1}\"", "bidderName", "{}");
    }

    @Test
    public void maybeLogBidderRequestShouldLimitLogBySpecLimit() {
        // given
        final AuctionContext givenAuctionContext =
                givenAuctionContext(accountBuilder -> accountBuilder.id("123"));
        final BidderRequest givenBidderRequest = givenBidderRequest(identity());
        final HttpLogSpec givenSpec = HttpLogSpec.of(null, null, "123", "bidderName", 1);

        // when
        testingInstance.setSpec(givenSpec);
        testingInstance.maybeLogBidderRequest(givenAuctionContext, givenBidderRequest);
        testingInstance.maybeLogBidderRequest(givenAuctionContext, givenBidderRequest);

        // then
        verify(logger).info(anyString(), anyString(), anyString());
    }

    @Test
    public void maybeLogBidderRequestShouldLogIfAccountIdAndBidderEqualsToGivenInSpec() {
        // given
        final AuctionContext givenAuctionContext =
                givenAuctionContext(accountBuilder -> accountBuilder.id("123"));
        final BidderRequest givenBidderRequest = givenBidderRequest(identity());
        final HttpLogSpec givenSpec = HttpLogSpec.of(null, null, "123", "bidderName", 1);

        // when
        testingInstance.setSpec(givenSpec);
        testingInstance.maybeLogBidderRequest(givenAuctionContext, givenBidderRequest);

        // then
        verify(logger).info(anyString(), anyString(), anyString());
    }

    @Test
    public void maybeLogBidderRequestShouldNotLogIfAccountIdEqualsToGivenInSpecButBidderNotEquals() {
        // given
        final AuctionContext givenAuctionContext =
                givenAuctionContext(accountBuilder -> accountBuilder.id("123"));
        final BidderRequest givenBidderRequest = givenBidderRequest(identity());
        final HttpLogSpec givenSpec = HttpLogSpec.of(null, null, "123", "anotherBidder", 1);

        // when
        testingInstance.setSpec(givenSpec);
        testingInstance.maybeLogBidderRequest(givenAuctionContext, givenBidderRequest);

        // then
        verifyNoInteractions(logger);
    }

    @Test
    public void maybeLogBidderRequestShouldLogIfRequestTypeIsOpenrtb2webAndSpecEndpointIsAuction() {
        // given
        final AuctionContext givenAuctionContext = givenAuctionContext(identity())
                .toBuilder()
                .requestTypeMetric(MetricName.openrtb2web)
                .build();
        final BidderRequest givenBidderRequest = givenBidderRequest(identity());
        final HttpLogSpec givenSpec = HttpLogSpec.of(HttpLogSpec.Endpoint.auction, null, null, "bidderName", 1);

        // when
        testingInstance.setSpec(givenSpec);
        testingInstance.maybeLogBidderRequest(givenAuctionContext, givenBidderRequest);

        // then
        verify(logger).info(anyString(), anyString(), anyString());
    }

    @Test
    public void maybeLogBidderRequestShouldLogIfRequestTypeIsOpenrtb2appAndSpecEndpointIsAuction() {
        // given
        final AuctionContext givenAuctionContext = givenAuctionContext(identity())
                .toBuilder()
                .requestTypeMetric(MetricName.openrtb2app)
                .build();
        final BidderRequest givenBidderRequest = givenBidderRequest(identity());
        final HttpLogSpec givenSpec = HttpLogSpec.of(HttpLogSpec.Endpoint.auction, null, null, "bidderName", 1);

        // when
        testingInstance.setSpec(givenSpec);
        testingInstance.maybeLogBidderRequest(givenAuctionContext, givenBidderRequest);

        // then
        verify(logger).info(anyString(), anyString(), anyString());
    }

    @Test
    public void maybeLogBidderRequestShouldLogIfRequestTypeIsAmpAndSpecEndpointIsAmp() {
        // given
        final AuctionContext givenAuctionContext = givenAuctionContext(identity())
                .toBuilder()
                .requestTypeMetric(MetricName.amp)
                .build();
        final BidderRequest givenBidderRequest = givenBidderRequest(identity());
        final HttpLogSpec givenSpec = HttpLogSpec.of(HttpLogSpec.Endpoint.amp, null, null, "bidderName", 1);

        // when
        testingInstance.setSpec(givenSpec);
        testingInstance.maybeLogBidderRequest(givenAuctionContext, givenBidderRequest);

        // then
        verify(logger).info(anyString(), anyString(), anyString());
    }

    @Test
    public void maybeLogBidderRequestShouldLogBidRequestWithChangeExtBidderToBidderName() {
        // given
        final AuctionContext givenAuctionContext = givenAuctionContext(identity())
                .toBuilder()
                .requestTypeMetric(MetricName.amp)
                .build();
        final BidderRequest givenBidderRequest = givenBidderRequest(
                bidRequestBuilder -> bidRequestBuilder
                        .imp(singletonList(Imp.builder()
                                .ext(mapper.createObjectNode()
                                        .set("bidder", mapper.createObjectNode()
                                                .set("param", new TextNode("value")))).build())));
        final HttpLogSpec givenSpec = HttpLogSpec.of(HttpLogSpec.Endpoint.amp, null, null, "bidderName", 1);

        // when
        testingInstance.setSpec(givenSpec);
        testingInstance.maybeLogBidderRequest(givenAuctionContext, givenBidderRequest);

        // then
        verify(logger).info(anyString(),
                anyString(),
                eq("{\"imp\":[{\"ext\":{\"bidderName\":{\"param\":\"value\"}}}]}"));
    }

    @Test
    public void maybeLogBidderRequestShouldTolerateMissingImpExt() {
        // given
        final AuctionContext givenAuctionContext = givenAuctionContext(identity())
                .toBuilder()
                .requestTypeMetric(MetricName.amp)
                .build();
        final BidderRequest givenBidderRequest = givenBidderRequest(
                bidRequestBuilder -> bidRequestBuilder
                        .imp(singletonList(Imp.builder().build())));
        final HttpLogSpec givenSpec = HttpLogSpec.of(HttpLogSpec.Endpoint.amp, null, null, "bidderName", 1);

        // when
        testingInstance.setSpec(givenSpec);
        testingInstance.maybeLogBidderRequest(givenAuctionContext, givenBidderRequest);

        // then
        verify(logger).info(anyString(), anyString(), anyString());
    }

    private static AuctionContext givenAuctionContext(UnaryOperator<Account.AccountBuilder> accountBuilderCustomizer) {
        final Account account = accountBuilderCustomizer.apply(Account.builder()).build();

        return AuctionContext.builder()
                .account(account)
                .build();
    }

    private static BidderRequest givenBidderRequest(
            UnaryOperator<BidRequest.BidRequestBuilder> bidRequestBuilderCustomizer) {
        final BidRequest bidRequest = bidRequestBuilderCustomizer.apply(BidRequest.builder()).build();

        return BidderRequest.of("bidderName", null, bidRequest);
    }
}
