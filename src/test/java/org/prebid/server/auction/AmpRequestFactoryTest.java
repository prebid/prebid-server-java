package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
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
import org.prebid.server.proto.openrtb.ext.request.ExtGranularityRange;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCacheBids;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCacheVastxml;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public class AmpRequestFactoryTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private TimeoutResolver timeoutResolver;
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
        given(timeoutResolver.resolve(any())).willReturn(2000L);

        given(httpRequest.getParam(eq("tag_id"))).willReturn("tagId");
        given(routingContext.request()).willReturn(httpRequest);

        factory = new AmpRequestFactory(timeoutResolver, storedRequestProcessor, auctionRequestFactory);
    }

    @Test
    public void shouldReturnFailedFutureIfRequestHasNoTagId() {
        // given
        given(httpRequest.getParam("tag_id")).willReturn(null);

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
    public void shouldReturnFailedFutureIfStoredBidRequestHasApp() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().build())
                .imp(singletonList(Imp.builder().build()))
                .build();
        given(storedRequestProcessor.processAmpRequest(anyString())).willReturn(Future.succeededFuture(bidRequest));

        // when
        final Future<BidRequest> future = factory.fromRequest(routingContext);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(InvalidRequestException.class);
        assertThat(((InvalidRequestException) future.cause()).getMessages())
                .hasSize(1).containsOnly("request.app must not exist in AMP stored requests.");
    }

    @Test
    public void shouldReturnFailedFutureIfStoredBidRequestHasNoExt() {
        // given
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
        final ObjectNode extBidRequest = mapper.valueToTree(ExtBidRequest.of(null));
        final BidRequest bidRequest = givenBidRequest(builder -> builder.ext(extBidRequest), Imp.builder().build());
        given(storedRequestProcessor.processAmpRequest(anyString())).willReturn(Future.succeededFuture(bidRequest));
        given(auctionRequestFactory.fillImplicitParameters(any(), any(), any())).willAnswer(answerWithFirstArgument());
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
                .containsExactly(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.of(Json.mapper.valueToTree(ExtPriceGranularity.of(2,
                                singletonList(ExtGranularityRange.of(BigDecimal.valueOf(20),
                                        BigDecimal.valueOf(0.1))))), null, null, true, true))
                        .cache(ExtRequestPrebidCache.of(ExtRequestPrebidCacheBids.of(null, null),
                                ExtRequestPrebidCacheVastxml.of(null, null)))
                        .build());
    }

    @Test
    public void shouldReturnBidRequestWithDefaultTargetingIfStoredBidRequestExtHasNoTargeting() {
        // given
        final BidRequest bidRequest = givenBidRequestWithExt(null, null);
        given(storedRequestProcessor.processAmpRequest(anyString())).willReturn(Future.succeededFuture(bidRequest));
        given(auctionRequestFactory.fillImplicitParameters(any(), any(), any())).willAnswer(answerWithFirstArgument());
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
                .containsExactly(tuple(
                        // default priceGranularity
                        mapper.valueToTree(ExtPriceGranularity.of(2, singletonList(ExtGranularityRange.of(
                                BigDecimal.valueOf(20), BigDecimal.valueOf(0.1))))),
                        // default includeWinners
                        true));
    }

    @Test
    public void shouldReturnBidRequestWithDefaultIncludeWinnersIfStoredBidRequestExtTargetingHasNoIncludeWinners() {
        // given
        final BidRequest bidRequest = givenBidRequestWithExt(
                ExtRequestTargeting.of(mapper.createObjectNode().put("foo", "bar"), null,
                        null, null, null), null);
        given(storedRequestProcessor.processAmpRequest(anyString())).willReturn(Future.succeededFuture(bidRequest));
        given(auctionRequestFactory.fillImplicitParameters(any(), any(), any())).willAnswer(answerWithFirstArgument());
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
    public void shouldReturnBidRequestWithIncludeWinnersFromStoredBidRequest() {
        // given
        final BidRequest bidRequest = givenBidRequestWithExt(
                ExtRequestTargeting.of(mapper.createObjectNode().put("foo", "bar"), null,
                        null, false, null), null);
        given(storedRequestProcessor.processAmpRequest(anyString())).willReturn(Future.succeededFuture(bidRequest));
        given(auctionRequestFactory.fillImplicitParameters(any(), any(), any())).willAnswer(answerWithFirstArgument());
        given(auctionRequestFactory.validateRequest(any())).willAnswer(answerWithFirstArgument());

        // when
        final Future<BidRequest> future = factory.fromRequest(routingContext);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(singletonList(future.result()))
                .extracting(BidRequest::getExt)
                .extracting(ext -> mapper.treeToValue(ext, ExtBidRequest.class)).isNotNull()
                .extracting(ExtBidRequest::getPrebid)
                .extracting(ExtRequestPrebid::getTargeting)
                .extracting(ExtRequestTargeting::getIncludewinners)
                .containsExactly(false);
    }

    @Test
    public void shouldReturnBidRequestWithDefaultIncludeBidderKeysIfStoredBidRequestExtTargetingHasNoIncludeBidderKeys() {
        // given
        final BidRequest bidRequest = givenBidRequestWithExt(
                ExtRequestTargeting.of(null, null, null, false, null), null);
        given(storedRequestProcessor.processAmpRequest(anyString())).willReturn(Future.succeededFuture(bidRequest));
        given(auctionRequestFactory.fillImplicitParameters(any(), any(), any())).willAnswer(answerWithFirstArgument());
        given(auctionRequestFactory.validateRequest(any())).willAnswer(answerWithFirstArgument());

        // when
        final Future<BidRequest> future = factory.fromRequest(routingContext);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(singletonList(future.result()))
                .extracting(BidRequest::getExt)
                .extracting(ext -> mapper.treeToValue(ext, ExtBidRequest.class)).isNotNull()
                .extracting(ExtBidRequest::getPrebid)
                .extracting(ExtRequestPrebid::getTargeting)
                .extracting(ExtRequestTargeting::getIncludewinners, ExtRequestTargeting::getIncludebidderkeys)
                // assert that includeBidderKeys was set with default value and includewinners remained unchanged
                .containsExactly(tuple(false, true));
    }

    @Test
    public void shouldReturnBidRequestWithIncludeBidderKeysFromStoredBidRequest() {
        // given
        final BidRequest bidRequest = givenBidRequestWithExt(
                ExtRequestTargeting.of(mapper.createObjectNode().put("foo", "bar"), null,
                        null, null, false), null);
        given(storedRequestProcessor.processAmpRequest(anyString())).willReturn(Future.succeededFuture(bidRequest));
        given(auctionRequestFactory.fillImplicitParameters(any(), any(), any())).willAnswer(answerWithFirstArgument());
        given(auctionRequestFactory.validateRequest(any())).willAnswer(answerWithFirstArgument());

        // when
        final Future<BidRequest> future = factory.fromRequest(routingContext);

        // then
        assertThat(future.succeeded()).isTrue();

        assertThat(singletonList(future.result()))
                .extracting(BidRequest::getExt)
                .extracting(ext -> mapper.treeToValue(ext, ExtBidRequest.class)).isNotNull()
                .extracting(ExtBidRequest::getPrebid)
                .extracting(ExtRequestPrebid::getTargeting)
                .extracting(ExtRequestTargeting::getIncludebidderkeys)
                .containsExactly(false);
    }

    @Test
    public void shouldReturnBidRequestWithDefaultPriceGranularityIfStoredBidRequestExtTargetingHasNoPriceGranularity() {
        // given
        final BidRequest bidRequest = givenBidRequestWithExt(
                ExtRequestTargeting.of(null, null, null, false, null), null);
        given(storedRequestProcessor.processAmpRequest(anyString())).willReturn(Future.succeededFuture(bidRequest));
        given(auctionRequestFactory.fillImplicitParameters(any(), any(), any())).willAnswer(answerWithFirstArgument());
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
                        tuple(false, mapper.valueToTree(ExtPriceGranularity.of(2, singletonList(
                                ExtGranularityRange.of(BigDecimal.valueOf(20), BigDecimal.valueOf(0.1)))))));
    }

    private Answer<Object> answerWithFirstArgument() {
        return invocationOnMock -> invocationOnMock.getArguments()[0];
    }

    @Test
    public void shouldReturnBidRequestWithDefaultCachingIfStoredBidRequestExtHasNoCaching() {
        // given
        final BidRequest bidRequest = givenBidRequestWithExt(null, null);
        given(storedRequestProcessor.processAmpRequest(anyString())).willReturn(Future.succeededFuture(bidRequest));
        given(auctionRequestFactory.fillImplicitParameters(any(), any(), any())).willAnswer(answerWithFirstArgument());
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
                .containsExactly(ExtRequestPrebidCacheBids.of(null, null));
    }

    @Test
    public void shouldReturnBidRequestWithImpSecureEqualsToOneIfInitiallyItWasNotSecured() {
        // given
        final BidRequest bidRequest = givenBidRequestWithExt(null, null);
        given(storedRequestProcessor.processAmpRequest(anyString())).willReturn(Future.succeededFuture(bidRequest));
        given(auctionRequestFactory.fillImplicitParameters(any(), any(), any())).willAnswer(answerWithFirstArgument());
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
        given(httpRequest.getParam("debug")).willReturn("1");

        final BidRequest bidRequest = givenBidRequest(
                builder -> builder
                        .test(0)
                        .ext(mapper.valueToTree(ExtBidRequest.of(null))),
                Imp.builder().build());
        given(storedRequestProcessor.processAmpRequest(anyString())).willReturn(Future.succeededFuture(bidRequest));

        // when
        factory.fromRequest(routingContext);

        // then
        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(auctionRequestFactory).fillImplicitParameters(captor.capture(), any(), any());

        assertThat(captor.getValue().getTest()).isEqualTo(1);
    }

    @Test
    public void shouldRespondWithBidRequestWithDebugFlagOn() throws JsonProcessingException {
        // given
        given(httpRequest.getParam("debug")).willReturn("1");

        final BidRequest bidRequest = givenBidRequest(
                builder -> builder
                        .ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder().debug(0).build()))),
                Imp.builder().build());
        given(storedRequestProcessor.processAmpRequest(anyString())).willReturn(Future.succeededFuture(bidRequest));

        // when
        factory.fromRequest(routingContext);

        // then
        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(auctionRequestFactory).fillImplicitParameters(captor.capture(), any(), any());

        final ExtBidRequest extBidRequest = Json.mapper.treeToValue(captor.getValue().getExt(), ExtBidRequest.class);
        assertThat(extBidRequest.getPrebid().getDebug()).isEqualTo(1);
    }

    @Test
    public void shouldReturnBidRequestWithOverriddenTagIdBySlotParamValue() {
        // given
        given(httpRequest.getParam("slot")).willReturn("Overridden-tagId");

        final BidRequest bidRequest = givenBidRequestWithExt(null, null);
        given(storedRequestProcessor.processAmpRequest(anyString())).willReturn(Future.succeededFuture(bidRequest));
        given(auctionRequestFactory.fillImplicitParameters(any(), any(), any())).willAnswer(answerWithFirstArgument());
        given(auctionRequestFactory.validateRequest(any())).willAnswer(answerWithFirstArgument());

        // when
        final Future<BidRequest> future = factory.fromRequest(routingContext);

        // then
        assertThat(future.succeeded()).isTrue();

        assertThat(singletonList(future.result()))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getTagid)
                .containsOnly("Overridden-tagId");
    }

    @Test
    public void shouldSetBidRequestSiteExt() {
        // given
        given(httpRequest.getParam("curl")).willReturn("");

        final BidRequest bidRequest = givenBidRequest(
                builder -> builder
                        .ext(mapper.valueToTree(ExtBidRequest.of(null))),
                Imp.builder().build());

        given(storedRequestProcessor.processAmpRequest(anyString())).willReturn(Future.succeededFuture(bidRequest));
        given(auctionRequestFactory.fillImplicitParameters(any(), any(), any())).willAnswer(answerWithFirstArgument());
        given(auctionRequestFactory.validateRequest(any())).willAnswer(answerWithFirstArgument());

        // when
        final Future<BidRequest> future = factory.fromRequest(routingContext);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(singletonList(future.result()))
                .extracting(BidRequest::getSite)
                .extracting(Site::getExt)
                .containsOnly(mapper.valueToTree(ExtSite.of(1, null)));
    }

    @Test
    public void shouldReturnBidRequestWithOverriddenSitePageByCurlParamValue() {
        // given
        given(httpRequest.getParam("curl")).willReturn("overridden-site-page");

        final BidRequest bidRequest = givenBidRequest(
                builder -> builder
                        .ext(mapper.valueToTree(ExtBidRequest.of(null)))
                        .site(Site.builder().page("will-be-overridden").build()),
                Imp.builder().build());

        given(storedRequestProcessor.processAmpRequest(anyString())).willReturn(Future.succeededFuture(bidRequest));
        given(auctionRequestFactory.fillImplicitParameters(any(), any(), any())).willAnswer(answerWithFirstArgument());
        given(auctionRequestFactory.validateRequest(any())).willAnswer(answerWithFirstArgument());

        // when
        final Future<BidRequest> future = factory.fromRequest(routingContext);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(singletonList(future.result()))
                .extracting(BidRequest::getSite)
                .extracting(Site::getPage, Site::getExt)
                .containsOnly(tuple("overridden-site-page", mapper.valueToTree(ExtSite.of(1, null))));
    }

    @Test
    public void shouldReturnBidRequestWithSitePageContainingCurlParamValueWhenSitePreviouslyNotExistInRequest() {
        // given
        given(httpRequest.getParam("curl")).willReturn("overridden-site-page");

        final BidRequest bidRequest = givenBidRequest(
                builder -> builder
                        .ext(mapper.valueToTree(ExtBidRequest.of(null)))
                        .site(null),
                Imp.builder().build());

        given(storedRequestProcessor.processAmpRequest(anyString())).willReturn(Future.succeededFuture(bidRequest));
        given(auctionRequestFactory.fillImplicitParameters(any(), any(), any())).willAnswer(answerWithFirstArgument());
        given(auctionRequestFactory.validateRequest(any())).willAnswer(answerWithFirstArgument());

        // when
        final Future<BidRequest> future = factory.fromRequest(routingContext);

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(singletonList(future.result()))
                .extracting(BidRequest::getSite)
                .extracting(Site::getPage, Site::getExt)
                .containsOnly(tuple("overridden-site-page", mapper.valueToTree(ExtSite.of(1, null))));
    }

    @Test
    public void shouldReturnRequestWithOverriddenBannerFormatByOverwriteWHParamsRespectingThemOverWH() {
        // given
        given(httpRequest.getParam("w")).willReturn("10");
        given(httpRequest.getParam("ow")).willReturn("1000");
        given(httpRequest.getParam("h")).willReturn("20");
        given(httpRequest.getParam("oh")).willReturn("2000");
        given(httpRequest.getParam("ms")).willReturn("44x88,66x99");

        final BidRequest bidRequest = givenBidRequest(
                builder -> builder
                        .ext(mapper.valueToTree(ExtBidRequest.of(null))),
                Imp.builder()
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder()
                                        .w(1)
                                        .h(2)
                                        .build()))
                                .build()).build());

        given(storedRequestProcessor.processAmpRequest(anyString())).willReturn(Future.succeededFuture(bidRequest));
        given(auctionRequestFactory.fillImplicitParameters(any(), any(), any())).willAnswer(answerWithFirstArgument());
        given(auctionRequestFactory.validateRequest(any())).willAnswer(answerWithFirstArgument());

        // when
        final Future<BidRequest> future = factory.fromRequest(routingContext);

        // then
        assertThat(singletonList(future.result()))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .flatExtracting(Banner::getFormat)
                .extracting(Format::getW, Format::getH)
                .containsOnly(tuple(1000, 2000), tuple(44, 88), tuple(66, 99));
    }

    @Test
    public void shouldReturnBidRequestWithOverriddenBannerFromOWAndHParamAndMultiListIfOHIsMissed() {
        // given
        given(httpRequest.getParam("ow")).willReturn("10");
        given(httpRequest.getParam("w")).willReturn("30");
        given(httpRequest.getParam("h")).willReturn("40");
        given(httpRequest.getParam("ms")).willReturn("50x60");

        final BidRequest bidRequest = givenBidRequest(
                builder -> builder
                        .ext(mapper.valueToTree(ExtBidRequest.of(null))),
                Imp.builder()
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder()
                                        .w(1)
                                        .h(2)
                                        .build()))
                                .build()).build());

        given(storedRequestProcessor.processAmpRequest(anyString())).willReturn(Future.succeededFuture(bidRequest));
        given(auctionRequestFactory.fillImplicitParameters(any(), any(), any())).willAnswer(answerWithFirstArgument());
        given(auctionRequestFactory.validateRequest(any())).willAnswer(answerWithFirstArgument());

        // when
        final Future<BidRequest> future = factory.fromRequest(routingContext);

        // then
        assertThat(singletonList(future.result()))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .flatExtracting(Banner::getFormat)
                .extracting(Format::getW, Format::getH)
                .containsOnly(tuple(10, 40), tuple(50, 60));
    }

    @Test
    public void shouldReturnBidRequestWithOverriddenBannerFromWAndOHParamAndMultiListIfOWIsMissed() {
        // given
        given(httpRequest.getParam("oh")).willReturn("20");
        given(httpRequest.getParam("w")).willReturn("30");
        given(httpRequest.getParam("h")).willReturn("40");
        given(httpRequest.getParam("ms")).willReturn("50x60");

        final BidRequest bidRequest = givenBidRequest(
                builder -> builder
                        .ext(mapper.valueToTree(ExtBidRequest.of(null))),
                Imp.builder()
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder()
                                        .w(1)
                                        .h(2)
                                        .build()))
                                .build()).build());

        given(storedRequestProcessor.processAmpRequest(anyString())).willReturn(Future.succeededFuture(bidRequest));
        given(auctionRequestFactory.fillImplicitParameters(any(), any(), any())).willAnswer(answerWithFirstArgument());
        given(auctionRequestFactory.validateRequest(any())).willAnswer(answerWithFirstArgument());

        // when
        final Future<BidRequest> future = factory.fromRequest(routingContext);

        // then
        assertThat(singletonList(future.result()))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .flatExtracting(Banner::getFormat)
                .extracting(Format::getW, Format::getH)
                .containsOnly(tuple(30, 20), tuple(50, 60));
    }

    @Test
    public void shouldReturnBidRequestWithBannerFromHWParamsAndMultiList() {
        // given
        given(httpRequest.getParam("w")).willReturn("30");
        given(httpRequest.getParam("h")).willReturn("40");
        given(httpRequest.getParam("ms")).willReturn("50x60");

        final BidRequest bidRequest = givenBidRequest(
                builder -> builder
                        .ext(mapper.valueToTree(ExtBidRequest.of(null))),
                Imp.builder()
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder()
                                        .w(1)
                                        .h(2)
                                        .build()))
                                .build()).build());

        given(storedRequestProcessor.processAmpRequest(anyString())).willReturn(Future.succeededFuture(bidRequest));
        given(auctionRequestFactory.fillImplicitParameters(any(), any(), any())).willAnswer(answerWithFirstArgument());
        given(auctionRequestFactory.validateRequest(any())).willAnswer(answerWithFirstArgument());

        // when
        final Future<BidRequest> future = factory.fromRequest(routingContext);

        // then
        assertThat(singletonList(future.result()))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .flatExtracting(Banner::getFormat)
                .extracting(Format::getW, Format::getH)
                .containsOnly(tuple(30, 40), tuple(50, 60));
    }

    @Test
    public void shouldReturnBidRequestWithOverriddenBannerFromWAndHParamsIfOwOhAndMultiListAreMissed() {
        // given
        given(httpRequest.getParam("w")).willReturn("30");
        given(httpRequest.getParam("h")).willReturn("40");

        final BidRequest bidRequest = givenBidRequest(
                builder -> builder
                        .ext(mapper.valueToTree(ExtBidRequest.of(null))),
                Imp.builder()
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder()
                                        .w(1)
                                        .h(2)
                                        .build()))
                                .build()).build());

        given(storedRequestProcessor.processAmpRequest(anyString())).willReturn(Future.succeededFuture(bidRequest));
        given(auctionRequestFactory.fillImplicitParameters(any(), any(), any())).willAnswer(answerWithFirstArgument());
        given(auctionRequestFactory.validateRequest(any())).willAnswer(answerWithFirstArgument());

        // when
        final Future<BidRequest> future = factory.fromRequest(routingContext);

        // then
        assertThat(singletonList(future.result()))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .flatExtracting(Banner::getFormat)
                .extracting(Format::getW, Format::getH)
                .containsOnly(tuple(30, 40));
    }

    @Test
    public void shouldReturnBidRequestWithUpdatedWidthForAllBannerFormatsWhenOnlyWIsPresentInParams() {
        // given
        given(httpRequest.getParam("w")).willReturn("30");

        final BidRequest bidRequest = givenBidRequest(
                builder -> builder
                        .ext(mapper.valueToTree(ExtBidRequest.of(null))),
                Imp.builder()
                        .banner(Banner.builder()
                                .format(asList(Format.builder().w(1).h(2).build(),
                                        Format.builder().w(3).h(4).build()))
                                .build()).build());

        given(storedRequestProcessor.processAmpRequest(anyString())).willReturn(Future.succeededFuture(bidRequest));
        given(auctionRequestFactory.fillImplicitParameters(any(), any(), any())).willAnswer(answerWithFirstArgument());
        given(auctionRequestFactory.validateRequest(any())).willAnswer(answerWithFirstArgument());

        // when
        final Future<BidRequest> future = factory.fromRequest(routingContext);

        // then
        assertThat(singletonList(future.result()))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .flatExtracting(Banner::getFormat)
                .extracting(Format::getW, Format::getH)
                .containsOnly(tuple(30, 2), tuple(30, 4));
    }

    @Test
    public void shouldReturnBidRequestWithUpdatedHeightForAllBannerFormatsWhenOnlyHIsPresentInParams() {
        // given
        given(httpRequest.getParam("h")).willReturn("40");

        final BidRequest bidRequest = givenBidRequest(
                builder -> builder
                        .ext(mapper.valueToTree(ExtBidRequest.of(null))),
                Imp.builder()
                        .banner(Banner.builder()
                                .format(asList(Format.builder().w(1).h(2).build(),
                                        Format.builder().w(3).h(4).build()))
                                .build()).build());

        given(storedRequestProcessor.processAmpRequest(anyString())).willReturn(Future.succeededFuture(bidRequest));
        given(auctionRequestFactory.fillImplicitParameters(any(), any(), any())).willAnswer(answerWithFirstArgument());
        given(auctionRequestFactory.validateRequest(any())).willAnswer(answerWithFirstArgument());

        // when
        final Future<BidRequest> future = factory.fromRequest(routingContext);

        // then
        assertThat(singletonList(future.result()))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .flatExtracting(Banner::getFormat)
                .extracting(Format::getW, Format::getH)
                .containsOnly(tuple(1, 40), tuple(3, 40));
    }

    @Test
    public void shouldReturnBidRequestWithOverriddenBannerFormatsByMultiSizeParams() {
        // given
        given(httpRequest.getParam("ms")).willReturn("44x88,66x99");

        final BidRequest bidRequest = givenBidRequest(
                builder -> builder
                        .ext(mapper.valueToTree(ExtBidRequest.of(null))),
                Imp.builder()
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder()
                                        .w(1)
                                        .h(2)
                                        .build()))
                                .build()).build());

        given(storedRequestProcessor.processAmpRequest(anyString())).willReturn(Future.succeededFuture(bidRequest));
        given(auctionRequestFactory.fillImplicitParameters(any(), any(), any())).willAnswer(answerWithFirstArgument());
        given(auctionRequestFactory.validateRequest(any())).willAnswer(answerWithFirstArgument());

        // when
        final Future<BidRequest> future = factory.fromRequest(routingContext);

        // then
        assertThat(singletonList(future.result()))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .flatExtracting(Banner::getFormat)
                .extracting(Format::getW, Format::getH)
                .containsOnly(tuple(44, 88), tuple(66, 99));
    }

    @Test
    public void shouldReturnBidRequestWithOriginalBannerFormatsWhenMultiSizeParamContainsCompletelyInvalidValue() {
        // given
        given(httpRequest.getParam("ms")).willReturn(",");

        final BidRequest bidRequest = givenBidRequest(
                builder -> builder
                        .ext(mapper.valueToTree(ExtBidRequest.of(null))),
                Imp.builder()
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder()
                                        .w(1)
                                        .h(2)
                                        .build()))
                                .build()).build());
        given(storedRequestProcessor.processAmpRequest(anyString())).willReturn(Future.succeededFuture(bidRequest));
        given(auctionRequestFactory.fillImplicitParameters(any(), any(), any())).willAnswer(answerWithFirstArgument());
        given(auctionRequestFactory.validateRequest(any())).willAnswer(answerWithFirstArgument());

        // when
        final Future<BidRequest> future = factory.fromRequest(routingContext);

        // then
        assertThat(singletonList(future.result()))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .flatExtracting(Banner::getFormat)
                .extracting(Format::getW, Format::getH)
                .containsOnly(tuple(1, 2));
    }

    @Test
    public void shouldReturnBidRequestWithOriginBannerFormatsWhenMultiSizeParamContainsAtLeastOneInvalidValue() {
        // given
        given(httpRequest.getParam("ms")).willReturn(",33x,44x77,abc,");

        final BidRequest bidRequest = givenBidRequest(
                builder -> builder
                        .ext(mapper.valueToTree(ExtBidRequest.of(null))),
                Imp.builder()
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder()
                                        .w(1)
                                        .h(2)
                                        .build()))
                                .build()).build());

        given(storedRequestProcessor.processAmpRequest(anyString())).willReturn(Future.succeededFuture(bidRequest));
        given(auctionRequestFactory.fillImplicitParameters(any(), any(), any())).willAnswer(answerWithFirstArgument());
        given(auctionRequestFactory.validateRequest(any())).willAnswer(answerWithFirstArgument());

        // when
        final Future<BidRequest> future = factory.fromRequest(routingContext);

        // then
        assertThat(singletonList(future.result()))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .flatExtracting(Banner::getFormat)
                .extracting(Format::getW, Format::getH)
                .containsOnly(tuple(1, 2));
    }

    @Test
    public void shouldReturnBidRequestOverriddenBannerFormatsWhenMsParamSizePairHasOneInvalidValue() {
        // given
        given(httpRequest.getParam("ms")).willReturn("900xZ");

        final BidRequest bidRequest = givenBidRequest(
                builder -> builder
                        .ext(mapper.valueToTree(ExtBidRequest.of(null))),
                Imp.builder()
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder()
                                        .w(1)
                                        .h(2)
                                        .build()))
                                .build()).build());

        given(storedRequestProcessor.processAmpRequest(anyString())).willReturn(Future.succeededFuture(bidRequest));
        given(auctionRequestFactory.fillImplicitParameters(any(), any(), any())).willAnswer(answerWithFirstArgument());
        given(auctionRequestFactory.validateRequest(any())).willAnswer(answerWithFirstArgument());

        // when
        final Future<BidRequest> future = factory.fromRequest(routingContext);

        // then
        assertThat(singletonList(future.result()))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .flatExtracting(Banner::getFormat)
                .extracting(Format::getW, Format::getH)
                .containsOnly(tuple(900, 0));
    }

    @Test
    public void shouldReturnBidRequestWithOriginBannerFormatsWhenMultiSizeParamContainsAtLeastOneZeroPairSize() {
        // given
        given(httpRequest.getParam("ms")).willReturn("44x77, 0x0");

        final BidRequest bidRequest = givenBidRequest(
                builder -> builder
                        .ext(mapper.valueToTree(ExtBidRequest.of(null))),
                Imp.builder()
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder()
                                        .w(1)
                                        .h(2)
                                        .build()))
                                .build()).build());

        given(storedRequestProcessor.processAmpRequest(anyString())).willReturn(Future.succeededFuture(bidRequest));
        given(auctionRequestFactory.fillImplicitParameters(any(), any(), any())).willAnswer(answerWithFirstArgument());
        given(auctionRequestFactory.validateRequest(any())).willAnswer(answerWithFirstArgument());

        // when
        final Future<BidRequest> future = factory.fromRequest(routingContext);

        // then
        assertThat(singletonList(future.result()))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .flatExtracting(Banner::getFormat)
                .extracting(Format::getW, Format::getH)
                .containsOnly(tuple(1, 2));
    }

    @Test
    public void shouldReturnBidRequestWithOverriddenBannerFormatsWhenMultiSizeParamContainsPartiallyInvalidParams() {
        // given
        given(httpRequest.getParam("ms")).willReturn("33x,44x77");

        final BidRequest bidRequest = givenBidRequest(
                builder -> builder
                        .ext(mapper.valueToTree(ExtBidRequest.of(null))),
                Imp.builder()
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder()
                                        .w(1)
                                        .h(2)
                                        .build()))
                                .build()).build());

        given(storedRequestProcessor.processAmpRequest(anyString())).willReturn(Future.succeededFuture(bidRequest));
        given(auctionRequestFactory.fillImplicitParameters(any(), any(), any())).willAnswer(answerWithFirstArgument());
        given(auctionRequestFactory.validateRequest(any())).willAnswer(answerWithFirstArgument());

        // when
        final Future<BidRequest> future = factory.fromRequest(routingContext);

        // then
        assertThat(singletonList(future.result()))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .flatExtracting(Banner::getFormat)
                .extracting(Format::getW, Format::getH)
                .containsOnly(tuple(33, 0), tuple(44, 77));
    }

    @Test
    public void shouldReturnBidRequestWithOriginBannerFormatsWhenAllParametersAreZero() {
        // given
        given(httpRequest.getParam("ow")).willReturn("0");
        given(httpRequest.getParam("oh")).willReturn("0");
        given(httpRequest.getParam("w")).willReturn("0");
        given(httpRequest.getParam("h")).willReturn("0");
        given(httpRequest.getParam("ms")).willReturn("0x0");

        final BidRequest bidRequest = givenBidRequest(
                builder -> builder
                        .ext(mapper.valueToTree(ExtBidRequest.of(null))),
                Imp.builder()
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder()
                                        .w(1)
                                        .h(2)
                                        .build()))
                                .build()).build());

        given(storedRequestProcessor.processAmpRequest(anyString())).willReturn(Future.succeededFuture(bidRequest));
        given(auctionRequestFactory.fillImplicitParameters(any(), any(), any())).willAnswer(answerWithFirstArgument());
        given(auctionRequestFactory.validateRequest(any())).willAnswer(answerWithFirstArgument());

        // when
        final Future<BidRequest> future = factory.fromRequest(routingContext);

        // then
        assertThat(singletonList(future.result()))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .flatExtracting(Banner::getFormat)
                .extracting(Format::getW, Format::getH)
                .containsOnly(tuple(1, 2));
    }

    @Test
    public void shouldReturnBidRequestWithOverriddenBannerWhenInvalidParamTreatedAsZeroValue() {
        // given
        given(httpRequest.getParam("ow")).willReturn("100");
        given(httpRequest.getParam("oh")).willReturn("invalid");
        given(httpRequest.getParam("h")).willReturn("200");

        final BidRequest bidRequest = givenBidRequest(
                builder -> builder
                        .ext(mapper.valueToTree(ExtBidRequest.of(null))),
                Imp.builder()
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder()
                                        .w(1)
                                        .h(2)
                                        .build()))
                                .build()).build());

        given(storedRequestProcessor.processAmpRequest(anyString())).willReturn(Future.succeededFuture(bidRequest));
        given(auctionRequestFactory.fillImplicitParameters(any(), any(), any())).willAnswer(answerWithFirstArgument());
        given(auctionRequestFactory.validateRequest(any())).willAnswer(answerWithFirstArgument());

        // when
        final Future<BidRequest> future = factory.fromRequest(routingContext);

        // then
        assertThat(singletonList(future.result()))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .flatExtracting(Banner::getFormat)
                .extracting(Format::getW, Format::getH)
                .containsOnly(tuple(100, 200));
    }

    @Test
    public void shouldReturnBidRequestWithOverriddenTmaxWhenTimeoutParamIsAvailable() {
        // given
        given(httpRequest.getParam("timeout")).willReturn("1000");

        final BidRequest bidRequest = givenBidRequest(
                builder -> builder
                        .ext(mapper.valueToTree(ExtBidRequest.of(null))),
                Imp.builder().build());

        given(storedRequestProcessor.processAmpRequest(anyString())).willReturn(Future.succeededFuture(bidRequest));
        given(auctionRequestFactory.fillImplicitParameters(any(), any(), any())).willAnswer(answerWithFirstArgument());
        given(auctionRequestFactory.validateRequest(any())).willAnswer(answerWithFirstArgument());

        // when
        final BidRequest result = factory.fromRequest(routingContext).result();

        // then
        assertThat(result.getTmax()).isEqualTo(1000L);
    }

    @Test
    public void shouldPassExtPrebidDebugFlagIfPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder
                        .ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                                .debug(1)
                                .build()))),
                Imp.builder().build());

        given(storedRequestProcessor.processAmpRequest(anyString())).willReturn(Future.succeededFuture(bidRequest));
        given(auctionRequestFactory.fillImplicitParameters(any(), any(), any())).willAnswer(answerWithFirstArgument());
        given(auctionRequestFactory.validateRequest(any())).willAnswer(answerWithFirstArgument());

        // when
        final BidRequest result = factory.fromRequest(routingContext).result();

        // then
        assertThat(singletonList(result))
                .extracting(BidRequest::getExt)
                .extracting(ext -> mapper.treeToValue(ext, ExtBidRequest.class))
                .extracting(ExtBidRequest::getPrebid)
                .extracting(ExtRequestPrebid::getDebug)
                .containsOnly(1);
    }

    private static BidRequest givenBidRequest(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestBuilderCustomizer,
            Imp... imps) {
        final List<Imp> impList = imps.length > 0 ? asList(imps) : null;

        return bidRequestBuilderCustomizer.apply(BidRequest.builder().imp(impList)).build();
    }

    private static BidRequest givenBidRequestWithExt(
            ExtRequestTargeting extRequestTargeting, ExtRequestPrebidCache extRequestPrebidCache) {
        return BidRequest.builder()
                .imp(singletonList(Imp.builder().build()))
                .ext(mapper.valueToTree(ExtBidRequest.of(
                        ExtRequestPrebid.builder()
                                .targeting(extRequestTargeting)
                                .cache(extRequestPrebidCache)
                                .build())))
                .build();
    }
}
