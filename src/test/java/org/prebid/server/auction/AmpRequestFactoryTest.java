package org.prebid.server.auction;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.proto.openrtb.ext.request.ExtBidRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
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
                .hasSize(1).containsOnly("AMP tag_id 'tagId' does not include an Imp object. One id required");
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
                .hasSize(1).containsOnly("AMP tag_id 'tagId' includes multiple Imp objects. We must have only one");
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
    public void shouldReturnFailedFutureIfStoredBidRequestExtHasNoTargeting() {
        // given
        given(httpRequest.getParam(anyString())).willReturn("tagId");

        given(storedRequestProcessor.processAmpRequest(anyString()))
                .willReturn(Future.succeededFuture(BidRequest.builder().imp(singletonList(Imp.builder().build()))
                        .ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.of(null, null, null, null))))
                        .build()));
        // when
        final Future<BidRequest> future = factory.fromRequest(routingContext);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(InvalidRequestException.class);
        assertThat(((InvalidRequestException) future.cause()).getMessages())
                .hasSize(1).containsOnly("request.ext.prebid.targeting is required for AMP requests");
    }

    @Test
    public void shouldRespondWithBadRequestIfStoredBidRequestExtHasNoCaching() {
        // given
        given(httpRequest.getParam(anyString())).willReturn("tagId");

        given(storedRequestProcessor.processAmpRequest(anyString()))
                .willReturn(Future.succeededFuture(BidRequest.builder().imp(singletonList(Imp.builder().build()))
                        .ext(mapper.valueToTree(ExtBidRequest.of(
                                ExtRequestPrebid.of(null, ExtRequestTargeting.of(null), null, null))))
                        .build()));
        // when
        final Future<BidRequest> future = factory.fromRequest(routingContext);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(InvalidRequestException.class);
        assertThat(((InvalidRequestException) future.cause()).getMessages())
                .hasSize(1).containsOnly("request.ext.prebid.cache.bids must be set to {} for AMP requests");
    }
}