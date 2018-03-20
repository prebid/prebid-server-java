package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;
import org.prebid.server.VertxTest;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.proto.openrtb.ext.request.ExtBidRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
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

        given(storedRequestProcessor.processAmpRequest(anyString()))
                .willReturn(Future.succeededFuture(BidRequest.builder().build()));

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

        given(storedRequestProcessor.processAmpRequest(anyString()))
                .willReturn(Future.succeededFuture(
                        BidRequest.builder().imp(asList(Imp.builder().build(), Imp.builder().build())).build()));

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

        given(storedRequestProcessor.processAmpRequest(anyString()))
                .willReturn(Future.succeededFuture(
                        BidRequest.builder().imp(singletonList(Imp.builder().build())).build()));
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

        final ObjectNode ext = ((ObjectNode) mapper.createObjectNode()
                .set("prebid", new TextNode("non-ExtBidRequest")));
        given(storedRequestProcessor.processAmpRequest(anyString()))
                .willReturn(Future.succeededFuture(
                        BidRequest.builder().imp(singletonList(Imp.builder().build())).ext(ext).build()));
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

        given(storedRequestProcessor.processAmpRequest(anyString()))
                .willReturn(Future.succeededFuture(
                        BidRequest.builder()
                                .imp(singletonList(Imp.builder().build()))
                                .ext(mapper.valueToTree(ExtBidRequest.of(null)))
                                .build()));

        given(auctionRequestFactory.fillImplicitParameters(any(), eq(routingContext)))
                .willAnswer(answerWithFirstArgument());
        given(auctionRequestFactory.validateRequest(any())).willAnswer(answerWithFirstArgument());

        // when
        final Future<BidRequest> future = factory.fromRequest(routingContext);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getExt())
                .extracting(ext -> Json.mapper.treeToValue(future.result().getExt(), ExtBidRequest.class)).isNotNull()
                .element(0)
                .extracting(extBidRequest -> extBidRequest.getPrebid())
                .containsExactly(ExtRequestPrebid.of(emptyMap(),
                        ExtRequestTargeting.of(CpmBucket.PriceGranularity.medium.name()), null,
                        ExtRequestPrebidCache.of(Json.mapper.createObjectNode())));
    }

    @Test
    public void shouldReturnBidRequestWithDefaultTargetingIfStoredBidRequestExtHasNoTargeting()
            throws JsonProcessingException {
        // given
        given(httpRequest.getParam(anyString())).willReturn("tagId");

        given(storedRequestProcessor.processAmpRequest(anyString()))
                .willReturn(Future.succeededFuture(BidRequest.builder().imp(singletonList(Imp.builder().build()))
                        .ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.of(null, null, null, null))))
                        .build()));

        given(auctionRequestFactory.fillImplicitParameters(any(), eq(routingContext)))
                .willAnswer(answerWithFirstArgument());
        given(auctionRequestFactory.validateRequest(any())).willAnswer(answerWithFirstArgument());

        // when
        final Future<BidRequest> future = factory.fromRequest(routingContext);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getExt())
                .extracting(ext -> Json.mapper.treeToValue(future.result().getExt(), ExtBidRequest.class)).isNotNull()
                .element(0)
                .extracting(extBidRequest -> extBidRequest.getPrebid().getTargeting().getPricegranularity())
                .containsExactly(CpmBucket.PriceGranularity.medium.name());
    }

    public Answer<Object> answerWithFirstArgument() {
        return invocationOnMock -> invocationOnMock.getArguments()[0];
    }

    @Test
    public void shouldReturnBidRequestWithDefaultCachingIfStoredBidRequestExtHasNoCaching()
            throws JsonProcessingException {
        // given
        given(httpRequest.getParam(anyString())).willReturn("tagId");

        given(storedRequestProcessor.processAmpRequest(anyString()))
                .willReturn(Future.succeededFuture(BidRequest.builder().imp(singletonList(Imp.builder().build()))
                        .ext(mapper.valueToTree(ExtBidRequest.of(
                                ExtRequestPrebid.of(null, null, null, null))))
                        .build()));

        given(auctionRequestFactory.fillImplicitParameters(any(), eq(routingContext)))
                .willAnswer(answerWithFirstArgument());
        given(auctionRequestFactory.validateRequest(any())).willAnswer(answerWithFirstArgument());

        // when
        final Future<BidRequest> future = factory.fromRequest(routingContext);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getExt())
                .extracting(ext -> Json.mapper.treeToValue(future.result().getExt(), ExtBidRequest.class)).isNotNull()
                .element(0)
                .extracting(extBidRequest -> extBidRequest.getPrebid().getCache().getBids())
                .containsExactly(Json.mapper.createObjectNode());
    }

    @Test
    public void shouldReturnBidRequestWithImpSecureEqualsToOneIfInitiallyItWasNotSecured() {
        // given
        given(httpRequest.getParam(anyString())).willReturn("tagId");

        given(storedRequestProcessor.processAmpRequest(anyString()))
                .willReturn(Future.succeededFuture(BidRequest.builder().imp(singletonList(Imp.builder().build()))
                        .ext(mapper.valueToTree(ExtBidRequest.of(
                                ExtRequestPrebid.of(null, ExtRequestTargeting.of(null), null, null))))
                        .build()));

        given(auctionRequestFactory.fillImplicitParameters(any(), eq(routingContext)))
                .willAnswer(answerWithFirstArgument());
        given(auctionRequestFactory.validateRequest(any())).willAnswer(answerWithFirstArgument());

        // when
        final Future<BidRequest> future = factory.fromRequest(routingContext);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result().getImp()).element(0).extracting(Imp::getSecure).containsExactly(1);
    }
}