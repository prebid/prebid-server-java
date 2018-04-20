package org.prebid.server.auction;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;
import org.prebid.server.VertxTest;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.proto.openrtb.ext.request.ExtBidRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularityBucket;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public class AmpRequestFactoryTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private StoredRequestProcessor storedRequestProcessor;
    @Mock
    private AuctionRequestFactory auctionRequestFactory;

    private AmpRequestFactory factory;

    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpRequest;

    @Before
    public void setUp() {
        given(routingContext.request()).willReturn(httpRequest);

        factory = new AmpRequestFactory(storedRequestProcessor, auctionRequestFactory);
    }

    @Test
    public void shouldReturnFailedFutureIfRequestHasNoTagId() {
        // given
        given(httpRequest.getParam(anyString())).willReturn(null);

        // when
        final Future<BidRequest> future = factory.fromRequest(routingContext);

        // then
        verifyZeroInteractions(storedRequestProcessor);
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(InvalidRequestException.class);
        assertThat(((InvalidRequestException) future.cause()).getMessages())
                .hasSize(1).containsOnly("AMP requests require an AMP tag_id");
    }

    @Test
    public void shouldReturnFailedFutureIfStoredBidRequestHasNoImp() {
        // given
        given(httpRequest.getParam(anyString())).willReturn("tagId");

        final BidRequest bidRequest = givenBidRequest(identity());
        given(storedRequestProcessor.processAmpRequest(anyString())).willReturn(Future.succeededFuture(bidRequest));

        // when
        final Future<BidRequest> future = factory.fromRequest(routingContext);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(InvalidRequestException.class);
        assertThat(((InvalidRequestException) future.cause()).getMessages())
                .hasSize(1).containsOnly("data for tag_id='tagId' does not define the required imp array.");
    }

    @Test
    public void shouldReturnFailedFutureIfStoredBidRequestHasMoreThenOneImp() {
        // given
        given(httpRequest.getParam(anyString())).willReturn("tagId");

        final Imp imp = Imp.builder().build();
        final BidRequest bidRequest = givenBidRequest(identity(), imp, imp);
        given(storedRequestProcessor.processAmpRequest(anyString())).willReturn(Future.succeededFuture(bidRequest));

        // when
        final Future<BidRequest> future = factory.fromRequest(routingContext);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(InvalidRequestException.class);
        assertThat(((InvalidRequestException) future.cause()).getMessages())
                .hasSize(1).containsOnly("data for tag_id 'tagId' includes 2 imp elements. Only one is allowed");
    }

    @Test
    public void shouldReturnFailedFutureIfStoredBidRequestHasNoExt() {
        // given
        given(httpRequest.getParam(anyString())).willReturn("tagId");

        final BidRequest bidRequest = givenBidRequest(identity(), Imp.builder().build());
        given(storedRequestProcessor.processAmpRequest(anyString())).willReturn(Future.succeededFuture(bidRequest));

        // when
        final Future<BidRequest> future = factory.fromRequest(routingContext);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(InvalidRequestException.class);
        assertThat(((InvalidRequestException) future.cause()).getMessages())
                .hasSize(1).containsOnly("AMP requests require Ext to be set");
    }

    @Test
    public void shouldReturnFailedFutureIfStoredBidRequestExtCouldNotBeParsed() {
        // given
        given(httpRequest.getParam(anyString())).willReturn("tagId");

        final ObjectNode ext = (ObjectNode) mapper.createObjectNode()
                .set("prebid", new TextNode("non-ExtBidRequest"));
        final BidRequest bidRequest = givenBidRequest(builder -> builder.ext(ext), Imp.builder().build());
        given(storedRequestProcessor.processAmpRequest(anyString())).willReturn(Future.succeededFuture(bidRequest));

        // when
        final Future<BidRequest> future = factory.fromRequest(routingContext);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(InvalidRequestException.class);
        assertThat(((InvalidRequestException) future.cause()).getMessages())
                .hasSize(1).element(0).asString().startsWith("Error decoding bidRequest.ext:");
    }

    @Test
    public void shouldReturnBidRequestWithDefaultPrebidValuesIfPrebidIsNull() {
        // given
        given(httpRequest.getParam(anyString())).willReturn("tagId");

        final ObjectNode extBidRequest = mapper.valueToTree(ExtBidRequest.of(null));
        final BidRequest bidRequest = givenBidRequest(builder -> builder.ext(extBidRequest),
                Imp.builder().build());
        given(storedRequestProcessor.processAmpRequest(anyString())).willReturn(Future.succeededFuture(bidRequest));

        given(auctionRequestFactory.fillImplicitParameters(any(), eq(routingContext)))
                .willAnswer(answerWithFirstArgument());
        given(auctionRequestFactory.validateRequest(any())).willAnswer(answerWithFirstArgument());

        // when
        final Future<BidRequest> future = factory.fromRequest(routingContext);

        // then
        assertThat(future.succeeded()).isTrue();

        // result was wrapped to list because extracting method works different on iterable and not iterable objects,
        // which force to make type casting or exception handling in lambdas
        assertThat(singletonList(future.result()))
                .extracting(BidRequest::getExt)
                .extracting(ext -> Json.mapper.treeToValue(ext, ExtBidRequest.class)).isNotNull()
                .extracting(ExtBidRequest::getPrebid)
                .containsExactly(ExtRequestPrebid.of(emptyMap(),
                       emptyMap(), ExtRequestTargeting.of(Json.mapper.valueToTree(
                        singletonList(ExtPriceGranularityBucket.of(2, new BigDecimal(0), new BigDecimal(20),
                                new BigDecimal("0.1")))), true), null,
                        ExtRequestPrebidCache.of(Json.mapper.createObjectNode())));
    }

    @Test
    public void shouldReturnBidRequestWithDefaultTargetingIfStoredBidRequestExtHasNoTargeting() {
        // given
        given(httpRequest.getParam(anyString())).willReturn("tagId");

        final BidRequest bidRequest = givenBidRequestWithExt(null, null);
        given(storedRequestProcessor.processAmpRequest(anyString())).willReturn(Future.succeededFuture(bidRequest));

        given(auctionRequestFactory.fillImplicitParameters(any(), eq(routingContext)))
                .willAnswer(answerWithFirstArgument());
        given(auctionRequestFactory.validateRequest(any())).willAnswer(answerWithFirstArgument());

        // when
        final Future<BidRequest> future = factory.fromRequest(routingContext);

        // then
        assertThat(future.succeeded()).isTrue();

        // result was wrapped to list because extracting method works different on iterable and not iterable objects,
        // which force to make type casting or exception handling in lambdas
        assertThat(singletonList(future.result()))
                .extracting(BidRequest::getExt)
                .extracting(ext -> mapper.treeToValue(ext, ExtBidRequest.class)).isNotNull()
                .extracting(ExtBidRequest::getPrebid)
                .extracting(ExtRequestPrebid::getTargeting)
                .extracting(ExtRequestTargeting::getPricegranularity, ExtRequestTargeting::getIncludewinners)
                .containsExactly(
                        tuple(
                                // default priceGranularity
                                mapper.valueToTree(singletonList(ExtPriceGranularityBucket.of(2, BigDecimal.valueOf(0),
                                        BigDecimal.valueOf(20), BigDecimal.valueOf(0.1)))),
                                // default includeWinners
                                true));
    }

    @Test
    public void shouldReturnBidRequestWithDefaultIncludeWinnersIfStoredBidRequestExtTargetingHasNoIncludeWinners() {
        // given
        given(httpRequest.getParam(anyString())).willReturn("tagId");

        final BidRequest bidRequest = givenBidRequestWithExt(
                ExtRequestTargeting.of(mapper.createObjectNode().put("foo", "bar"), null), null);
        given(storedRequestProcessor.processAmpRequest(anyString())).willReturn(Future.succeededFuture(bidRequest));

        given(auctionRequestFactory.fillImplicitParameters(any(), eq(routingContext)))
                .willAnswer(answerWithFirstArgument());
        given(auctionRequestFactory.validateRequest(any())).willAnswer(answerWithFirstArgument());

        // when
        final Future<BidRequest> future = factory.fromRequest(routingContext);

        // then
        assertThat(future.succeeded()).isTrue();

        // result was wrapped to list because extracting method works different on iterable and not iterable objects,
        // which force to make type casting or exception handling in lambdas
        assertThat(singletonList(future.result()))
                .extracting(BidRequest::getExt)
                .extracting(ext -> mapper.treeToValue(ext, ExtBidRequest.class)).isNotNull()
                .extracting(ExtBidRequest::getPrebid)
                .extracting(ExtRequestPrebid::getTargeting)
                .extracting(ExtRequestTargeting::getIncludewinners, ExtRequestTargeting::getPricegranularity)
                // assert that includeWinners was set with default value and priceGranularity remained unchanged
                .containsExactly(
                        tuple(true, mapper.createObjectNode().put("foo", "bar")));
    }

    @Test
    public void shouldReturnBidRequestWithDefaultPriceGranularityIfStoredBidRequestExtTargetingHasNoPriceGranularity() {
        // given
        given(httpRequest.getParam(anyString())).willReturn("tagId");

        final BidRequest bidRequest = givenBidRequestWithExt(
                ExtRequestTargeting.of(null, false), null);
        given(storedRequestProcessor.processAmpRequest(anyString())).willReturn(Future.succeededFuture(bidRequest));

        given(auctionRequestFactory.fillImplicitParameters(any(), eq(routingContext)))
                .willAnswer(answerWithFirstArgument());
        given(auctionRequestFactory.validateRequest(any())).willAnswer(answerWithFirstArgument());

        // when
        final Future<BidRequest> future = factory.fromRequest(routingContext);

        // then
        assertThat(future.succeeded()).isTrue();

        // result was wrapped to list because extracting method works different on iterable and not iterable objects,
        // which force to make type casting or exception handling in lambdas
        assertThat(singletonList(future.result()))
                .extracting(BidRequest::getExt)
                .extracting(ext -> mapper.treeToValue(ext, ExtBidRequest.class)).isNotNull()
                .extracting(ExtBidRequest::getPrebid)
                .extracting(ExtRequestPrebid::getTargeting)
                .extracting(ExtRequestTargeting::getIncludewinners, ExtRequestTargeting::getPricegranularity)
                // assert that priceGranularity was set with default value and includeWinners remained unchanged
                .containsExactly(
                        tuple(
                                false,
                                mapper.valueToTree(singletonList(ExtPriceGranularityBucket.of(2, BigDecimal.valueOf(0),
                                        BigDecimal.valueOf(20), BigDecimal.valueOf(0.1))))));
    }

    private Answer<Object> answerWithFirstArgument() {
        return invocationOnMock -> invocationOnMock.getArguments()[0];
    }

    @Test
    public void shouldReturnBidRequestWithDefaultCachingIfStoredBidRequestExtHasNoCaching() {
        // given
        given(httpRequest.getParam(anyString())).willReturn("tagId");

        final BidRequest bidRequest = givenBidRequestWithExt(null, null);
        given(storedRequestProcessor.processAmpRequest(anyString())).willReturn(Future.succeededFuture(bidRequest));

        given(auctionRequestFactory.fillImplicitParameters(any(), eq(routingContext)))
                .willAnswer(answerWithFirstArgument());
        given(auctionRequestFactory.validateRequest(any())).willAnswer(answerWithFirstArgument());

        // when
        final Future<BidRequest> future = factory.fromRequest(routingContext);

        // then
        assertThat(future.succeeded()).isTrue();

        // result was wrapped to list because extracting method works different on iterable and not iterable objects,
        // which force to make type casting or exception handling in lambdas
        assertThat(singletonList(future.result()))
                .extracting(BidRequest::getExt)
                .extracting(ext -> Json.mapper.treeToValue(future.result().getExt(), ExtBidRequest.class)).isNotNull()
                .extracting(extBidRequest -> extBidRequest.getPrebid().getCache().getBids())
                .containsExactly(Json.mapper.createObjectNode());
    }

    @Test
    public void shouldReturnBidRequestWithImpSecureEqualsToOneIfInitiallyItWasNotSecured() {
        // given
        given(httpRequest.getParam(anyString())).willReturn("tagId");

        final BidRequest bidRequest = givenBidRequestWithExt(null, null);
        given(storedRequestProcessor.processAmpRequest(anyString())).willReturn(Future.succeededFuture(bidRequest));

        given(auctionRequestFactory.fillImplicitParameters(any(), eq(routingContext)))
                .willAnswer(answerWithFirstArgument());
        given(auctionRequestFactory.validateRequest(any())).willAnswer(answerWithFirstArgument());

        // when
        final Future<BidRequest> future = factory.fromRequest(routingContext);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getImp()).element(0).extracting(Imp::getSecure).containsExactly(1);
    }

    @Test
    public void shouldRespondWithBidRequestWithTestFlagOn() {
        // given
        given(httpRequest.getParam("tag_id")).willReturn("tagId");
        given(httpRequest.getParam("debug")).willReturn("1");

        final BidRequest bidRequest = givenBidRequestWithExt(ExtRequestTargeting.of(null, null),
                ExtRequestPrebidCache.of(mapper.createObjectNode()));

        given(storedRequestProcessor.processAmpRequest(anyString())).willReturn(Future.succeededFuture(bidRequest));

        // when
        factory.fromRequest(routingContext);

        // then
        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(auctionRequestFactory).fillImplicitParameters(captor.capture(), any());

        assertThat(captor.getValue().getTest()).isEqualTo(1);
    }

    private static BidRequest givenBidRequest(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestBuilderCustomizer,
            Imp... imps) {
        final List<Imp> impList = imps.length > 0 ? Arrays.asList(imps) : null;

        return bidRequestBuilderCustomizer.apply(BidRequest.builder().imp(impList)).build();
    }

    private static BidRequest givenBidRequestWithExt(
            ExtRequestTargeting extRequestTargeting, ExtRequestPrebidCache extRequestPrebidCache) {
        return BidRequest.builder()
                .imp(singletonList(Imp.builder().build()))
                .ext(mapper.valueToTree(ExtBidRequest.of(
                        ExtRequestPrebid.of(null, null, extRequestTargeting, null, extRequestPrebidCache))))
                .build();
    }
}
