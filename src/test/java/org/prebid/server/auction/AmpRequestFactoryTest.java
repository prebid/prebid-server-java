package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerRequest;
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
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.proto.openrtb.ext.request.ExtGranularityRange;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCacheBids;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCacheVastxml;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidData;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.request.Targeting;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    @Mock
    private TimeoutResolver timeoutResolver;

    private AmpRequestFactory factory;
    @Mock
    private HttpServerRequest httpRequest;
    @Mock
    private RoutingContext routingContext;
    @Mock
    private FpdResolver fpdResolver;

    @Before
    public void setUp() {
        given(timeoutResolver.resolve(any())).willReturn(2000L);
        given(timeoutResolver.adjustTimeout(anyLong())).willReturn(1900L);

        given(httpRequest.getParam(eq("tag_id"))).willReturn("tagId");
        given(routingContext.request()).willReturn(httpRequest);
        given(fpdResolver.resolveApp(any(), any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(fpdResolver.resolveSite(any(), any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(fpdResolver.resolveUser(any(), any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(fpdResolver.resolveImpExt(any(), any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(fpdResolver.resolveBidRequestExt(any(), any())).willAnswer(invocationOnMock -> invocationOnMock
                .getArgument(0));

        factory = new AmpRequestFactory(
                storedRequestProcessor, auctionRequestFactory, fpdResolver, timeoutResolver, jacksonMapper);
    }

    @Test
    public void shouldReturnFailedFutureIfRequestHasNoTagId() {
        // given
        given(httpRequest.getParam("tag_id")).willReturn(null);

        // when
        final Future<?> future = factory.fromRequest(routingContext, 0L);

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
        givenBidRequest(identity());

        // when
        final Future<?> future = factory.fromRequest(routingContext, 0L);

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
        givenBidRequest(identity(), imp, imp);

        // when
        final Future<?> future = factory.fromRequest(routingContext, 0L);

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
        final Future<?> future = factory.fromRequest(routingContext, 0L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(InvalidRequestException.class);
        assertThat(((InvalidRequestException) future.cause()).getMessages())
                .hasSize(1).containsOnly("request.app must not exist in AMP stored requests.");
    }

    @Test
    public void shouldReturnFailedFutureIfStoredBidRequestHasNoExt() {
        // given
        givenBidRequest(identity(), Imp.builder().build());

        // when
        final Future<?> future = factory.fromRequest(routingContext, 0L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(InvalidRequestException.class);
        assertThat(((InvalidRequestException) future.cause()).getMessages())
                .hasSize(1).containsOnly("AMP requests require Ext to be set");
    }

    @Test
    public void shouldReturnBidRequestWithDefaultPrebidValuesIfPrebidIsNull() {
        // given
        givenBidRequest(builder -> builder.ext(ExtRequest.empty()), Imp.builder().build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        // result was wrapped to list because extracting method works different on iterable and not iterable objects,
        // which force to make type casting or exception handling in lambdas
        assertThat(singletonList(request))
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .containsExactly(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.builder()
                                .pricegranularity(mapper.valueToTree(ExtPriceGranularity.of(2,
                                        singletonList(ExtGranularityRange.of(BigDecimal.valueOf(20),
                                                BigDecimal.valueOf(0.1))))))
                                .includewinners(true)
                                .includebidderkeys(true)
                                .build())
                        .cache(ExtRequestPrebidCache.of(ExtRequestPrebidCacheBids.of(null, null),
                                ExtRequestPrebidCacheVastxml.of(null, null), null))
                        .build());
    }

    @Test
    public void shouldReturnBidRequestWithDefaultTargetingIfStoredBidRequestExtHasNoTargeting() {
        // given
        givenBidRequest(
                builder -> builder
                        .ext(givenRequestExt(null)),
                Imp.builder().build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .extracting(BidRequest::getExt).isNotNull()
                .extracting(ExtRequest::getPrebid)
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
        givenBidRequest(
                builder -> builder
                        .ext(givenRequestExt(
                                ExtRequestTargeting.builder()
                                        .pricegranularity(mapper.createObjectNode().put("foo", "bar"))
                                        .build())),
                Imp.builder().build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .extracting(BidRequest::getExt).isNotNull()
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getTargeting)
                .extracting(ExtRequestTargeting::getIncludewinners, ExtRequestTargeting::getPricegranularity)
                // assert that includeWinners was set with default value and priceGranularity remained unchanged
                .containsExactly(
                        tuple(true, mapper.createObjectNode().put("foo", "bar")));
    }

    @Test
    public void shouldReturnBidRequestWithIncludeWinnersFromStoredBidRequest() {
        // given
        givenBidRequest(
                builder -> builder
                        .ext(givenRequestExt(
                                ExtRequestTargeting.builder()
                                        .pricegranularity(mapper.createObjectNode().put("foo", "bar"))
                                        .includewinners(false)
                                        .build())),
                Imp.builder().build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .extracting(BidRequest::getExt).isNotNull()
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getTargeting)
                .extracting(ExtRequestTargeting::getIncludewinners)
                .containsExactly(false);
    }

    @Test
    public void shouldReturnBidRequestWithDefaultIncludeBidderKeysIfStoredRequestExtTargetingHasNoIncludeBidderKeys() {
        // given
        givenBidRequest(
                builder -> builder
                        .ext(givenRequestExt(ExtRequestTargeting.builder().includewinners(false).build())),
                Imp.builder().build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .extracting(BidRequest::getExt).isNotNull()
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getTargeting)
                .extracting(ExtRequestTargeting::getIncludewinners, ExtRequestTargeting::getIncludebidderkeys)
                // assert that includeBidderKeys was set with default value and includewinners remained unchanged
                .containsExactly(tuple(false, true));
    }

    @Test
    public void shouldReturnBidRequestWithIncludeBidderKeysFromStoredBidRequest() {
        // given
        givenBidRequest(
                builder -> builder
                        .ext(givenRequestExt(
                                ExtRequestTargeting.builder()
                                        .pricegranularity(mapper.createObjectNode().put("foo", "bar"))
                                        .includebidderkeys(false)
                                        .build())),
                Imp.builder().build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .extracting(BidRequest::getExt).isNotNull()
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getTargeting)
                .extracting(ExtRequestTargeting::getIncludebidderkeys)
                .containsExactly(false);
    }

    @Test
    public void shouldReturnBidRequestWithDefaultPriceGranularityIfStoredBidRequestExtTargetingHasNoPriceGranularity() {
        // given
        givenBidRequest(
                builder -> builder
                        .ext(givenRequestExt(ExtRequestTargeting.builder().includewinners(false).build())),
                Imp.builder().build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .extracting(BidRequest::getExt).isNotNull()
                .extracting(ExtRequest::getPrebid)
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
        givenBidRequest(
                builder -> builder
                        .ext(givenRequestExt(null)),
                Imp.builder().build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .extracting(BidRequest::getExt).isNotNull()
                .extracting(extBidRequest -> extBidRequest.getPrebid().getCache().getBids())
                .containsExactly(ExtRequestPrebidCacheBids.of(null, null));
    }

    @Test
    public void shouldReturnBidRequestWithImpSecureEqualsToOneIfInitiallyItWasNotSecured() {
        // given
        givenBidRequest(
                builder -> builder
                        .ext(givenRequestExt(null)),
                Imp.builder().build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getSecure)
                .containsExactly(1);
    }

    @Test
    public void shouldRespondWithBidRequestWithTestFlagOn() {
        // given
        given(httpRequest.getParam("debug")).willReturn("1");

        givenBidRequest(
                builder -> builder
                        .test(0)
                        .ext(ExtRequest.empty()),
                Imp.builder().build());

        // when
        factory.fromRequest(routingContext, 0L);

        // then
        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(auctionRequestFactory).fillImplicitParameters(captor.capture(), any(), any());

        assertThat(captor.getValue().getTest()).isEqualTo(1);
    }

    @Test
    public void shouldRespondWithBidRequestWithDebugFlagOn() {
        // given
        given(httpRequest.getParam("debug")).willReturn("1");

        givenBidRequest(
                builder -> builder
                        .ext(ExtRequest.of(ExtRequestPrebid.builder().debug(0).build())),
                Imp.builder().build());

        // when
        factory.fromRequest(routingContext, 0L);

        // then
        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(auctionRequestFactory).fillImplicitParameters(captor.capture(), any(), any());

        final ExtRequest extRequest = captor.getValue().getExt();
        assertThat(extRequest.getPrebid().getDebug()).isEqualTo(1);
    }

    @Test
    public void shouldReturnBidRequestWithOverriddenTagIdBySlotParamValue() {
        // given
        given(httpRequest.getParam("slot")).willReturn("Overridden-tagId");

        givenBidRequest(
                builder -> builder
                        .ext(givenRequestExt(null)),
                Imp.builder().build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getTagid)
                .containsOnly("Overridden-tagId");
    }

    @Test
    public void shouldSetBidRequestSiteExt() {
        // given
        given(httpRequest.getParam("curl")).willReturn("");

        givenBidRequest(
                builder -> builder
                        .ext(ExtRequest.empty()),
                Imp.builder().build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .extracting(BidRequest::getSite)
                .extracting(Site::getExt)
                .containsOnly(ExtSite.of(1, null));
    }

    @Test
    public void shouldReturnBidRequestWithOverriddenSitePageByCurlParamValue() {
        // given
        given(httpRequest.getParam("curl")).willReturn("overridden-site-page");

        givenBidRequest(
                builder -> builder
                        .ext(ExtRequest.empty())
                        .site(Site.builder().page("will-be-overridden").build()),
                Imp.builder().build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .extracting(BidRequest::getSite)
                .extracting(Site::getPage, Site::getExt)
                .containsOnly(tuple("overridden-site-page", ExtSite.of(1, null)));
    }

    @Test
    public void shouldReturnBidRequestWithSitePageContainingCurlParamValueWhenSitePreviouslyNotExistInRequest() {
        // given
        given(httpRequest.getParam("curl")).willReturn("overridden-site-page");

        givenBidRequest(
                builder -> builder
                        .ext(ExtRequest.empty())
                        .site(null),
                Imp.builder().build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .extracting(BidRequest::getSite)
                .extracting(Site::getPage, Site::getExt)
                .containsOnly(tuple("overridden-site-page", ExtSite.of(1, null)));
    }

    @Test
    public void shouldReturnBidRequestWithSitePublisherIdOverriddenWithAccountParamValue() {
        // given
        given(httpRequest.getParam("account")).willReturn("accountId");

        givenBidRequest(
                builder -> builder
                        .ext(ExtRequest.empty())
                        .site(Site.builder().publisher(Publisher.builder().id("will-be-overridden").build()).build()),
                Imp.builder().build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .extracting(BidRequest::getSite)
                .extracting(Site::getPublisher, Site::getExt)
                .containsOnly(tuple(
                        Publisher.builder().id("accountId").build(),
                        ExtSite.of(1, null)));
    }

    @Test
    public void shouldReturnBidRequestWithSitePublisherIdFromAccountParamWhenSiteDoesNotExist() {
        // given
        given(httpRequest.getParam("account")).willReturn("accountId");

        givenBidRequest(
                builder -> builder
                        .ext(ExtRequest.empty())
                        .site(null),
                Imp.builder().build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .extracting(BidRequest::getSite)
                .extracting(Site::getPublisher, Site::getExt)
                .containsOnly(tuple(
                        Publisher.builder().id("accountId").build(),
                        ExtSite.of(1, null)));
    }

    @Test
    public void shouldReturnBidRequestWithSitePublisherIdFromAccountParamWhenSitePublisherDoesNotExist() {
        // given
        given(httpRequest.getParam("account")).willReturn("accountId");

        givenBidRequest(
                builder -> builder
                        .ext(ExtRequest.empty())
                        .site(Site.builder().build()),
                Imp.builder().build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .extracting(BidRequest::getSite)
                .extracting(Site::getPublisher, Site::getExt)
                .containsOnly(tuple(
                        Publisher.builder().id("accountId").build(),
                        ExtSite.of(1, null)));
    }

    @Test
    public void shouldReturnBidRequestWithSiteUpdatedByFpdResolver() throws JsonProcessingException {
        // given
        given(httpRequest.getParam("targeting"))
                .willReturn(mapper.writeValueAsString(Targeting.of(null, mapper.createObjectNode(), null)));

        given(fpdResolver.resolveSite(any(), any())).willReturn(Site.builder().id("siteId").build());

        givenBidRequest(
                builder -> builder
                        .ext(ExtRequest.empty())
                        .site(Site.builder().build()),
                Imp.builder().build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        verify(fpdResolver).resolveSite(any(), any());
        assertThat(request)
                .extracting(BidRequest::getSite)
                .containsOnly(Site.builder().id("siteId").build());
    }

    @Test
    public void shouldReturnRequestWithOverriddenBannerFormatByOverwriteWHParamsRespectingThemOverWH() {
        // given
        given(httpRequest.getParam("w")).willReturn("10");
        given(httpRequest.getParam("ow")).willReturn("1000");
        given(httpRequest.getParam("h")).willReturn("20");
        given(httpRequest.getParam("oh")).willReturn("2000");
        given(httpRequest.getParam("ms")).willReturn("44x88,66x99");

        givenBidRequest(
                builder -> builder
                        .ext(ExtRequest.empty()),
                Imp.builder()
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder()
                                        .w(1)
                                        .h(2)
                                        .build()))
                                .build()).build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
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

        givenBidRequest(
                builder -> builder
                        .ext(ExtRequest.empty()),
                Imp.builder()
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder()
                                        .w(1)
                                        .h(2)
                                        .build()))
                                .build()).build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
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

        givenBidRequest(
                builder -> builder
                        .ext(ExtRequest.empty()),
                Imp.builder()
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder()
                                        .w(1)
                                        .h(2)
                                        .build()))
                                .build()).build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
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

        givenBidRequest(
                builder -> builder
                        .ext(ExtRequest.empty()),
                Imp.builder()
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder()
                                        .w(1)
                                        .h(2)
                                        .build()))
                                .build()).build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
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

        givenBidRequest(
                builder -> builder
                        .ext(ExtRequest.empty()),
                Imp.builder()
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder()
                                        .w(1)
                                        .h(2)
                                        .build()))
                                .build()).build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
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

        givenBidRequest(
                builder -> builder
                        .ext(ExtRequest.empty()),
                Imp.builder()
                        .banner(Banner.builder()
                                .format(asList(Format.builder().w(1).h(2).build(),
                                        Format.builder().w(3).h(4).build()))
                                .build()).build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
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

        givenBidRequest(
                builder -> builder
                        .ext(ExtRequest.empty()),
                Imp.builder()
                        .banner(Banner.builder()
                                .format(asList(Format.builder().w(1).h(2).build(),
                                        Format.builder().w(3).h(4).build()))
                                .build()).build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
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

        givenBidRequest(
                builder -> builder
                        .ext(ExtRequest.empty()),
                Imp.builder()
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder()
                                        .w(1)
                                        .h(2)
                                        .build()))
                                .build()).build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
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

        givenBidRequest(
                builder -> builder
                        .ext(ExtRequest.empty()),
                Imp.builder()
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder()
                                        .w(1)
                                        .h(2)
                                        .build()))
                                .build()).build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
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

        givenBidRequest(
                builder -> builder
                        .ext(ExtRequest.empty()),
                Imp.builder()
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder()
                                        .w(1)
                                        .h(2)
                                        .build()))
                                .build()).build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
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

        givenBidRequest(
                builder -> builder
                        .ext(ExtRequest.empty()),
                Imp.builder()
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder()
                                        .w(1)
                                        .h(2)
                                        .build()))
                                .build()).build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
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

        givenBidRequest(
                builder -> builder
                        .ext(ExtRequest.empty()),
                Imp.builder()
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder()
                                        .w(1)
                                        .h(2)
                                        .build()))
                                .build()).build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
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

        givenBidRequest(
                builder -> builder
                        .ext(ExtRequest.empty()),
                Imp.builder()
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder()
                                        .w(1)
                                        .h(2)
                                        .build()))
                                .build()).build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
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

        givenBidRequest(
                builder -> builder
                        .ext(ExtRequest.empty()),
                Imp.builder()
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder()
                                        .w(1)
                                        .h(2)
                                        .build()))
                                .build()).build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
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

        givenBidRequest(
                builder -> builder
                        .ext(ExtRequest.empty()),
                Imp.builder()
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder()
                                        .w(1)
                                        .h(2)
                                        .build()))
                                .build()).build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
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

        givenBidRequest(
                builder -> builder
                        .ext(ExtRequest.empty()),
                Imp.builder().build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(request.getTmax()).isEqualTo(1000L);
    }

    @Test
    public void shouldReturnBidRequestWithUnmodifiedUserWhenGdprConsentParamIsNullOrBlank() {
        // given
        given(httpRequest.getParam("gdpr_consent")).willReturn(null, "");

        givenBidRequest(
                builder -> builder
                        .user(User.builder()
                                .ext(ExtUser.builder().consent("should-remain").build())
                                .build())
                        .ext(ExtRequest.empty()),
                Imp.builder().build());

        // when
        final BidRequest firstResult = factory.fromRequest(routingContext, 0L).result().getBidRequest();
        final BidRequest secondResult = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        final User expectedUser = User.builder()
                .ext(ExtUser.builder().consent("should-remain").build())
                .build();

        assertThat(firstResult.getUser()).isEqualTo(expectedUser);
        assertThat(secondResult.getUser()).isEqualTo(expectedUser);
    }

    @Test
    public void shouldReturnBidRequestWithUserUpdatedByFpdResolver() throws JsonProcessingException {
        // given
        given(httpRequest.getParam("targeting"))
                .willReturn(mapper.writeValueAsString(Targeting.of(null, null, mapper.createObjectNode())));

        given(fpdResolver.resolveUser(any(), any())).willReturn(User.builder().id("userId").build());

        givenBidRequest(
                builder -> builder
                        .ext(ExtRequest.empty())
                        .user(User.builder().build()),
                Imp.builder().build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        verify(fpdResolver).resolveUser(any(), any());
        assertThat(request)
                .extracting(BidRequest::getUser)
                .containsOnly(User.builder().id("userId").build());
    }

    @Test
    public void shouldReturnBidRequestWithOverriddenUserExtConsentWhenGdprConsentParamIsValid() {
        // given
        given(httpRequest.getParam("gdpr_consent")).willReturn("BONV8oqONXwgmADACHENAO7pqzAAppY");

        givenBidRequest(
                builder -> builder
                        .user(User.builder()
                                .id("1")
                                .ext(ExtUser.builder().consent("should-be-overridden").build())
                                .build())
                        .ext(ExtRequest.empty()),
                Imp.builder().build());

        // when
        final BidRequest result = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(result.getUser())
                .isEqualTo(User.builder()
                        .id("1")
                        .ext(ExtUser.builder().consent("BONV8oqONXwgmADACHENAO7pqzAAppY").build())
                        .build());
    }

    @Test
    public void shouldReturnBidRequestWithNewUserThatContainsUserExtConsentWhenInitialUserIsMissing() {
        // given
        given(httpRequest.getParam("gdpr_consent")).willReturn("BONV8oqONXwgmADACHENAO7pqzAAppY");

        givenBidRequest(
                builder -> builder
                        .ext(ExtRequest.empty()),
                Imp.builder().build());

        // when
        final BidRequest result = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(result.getUser())
                .isEqualTo(User.builder()
                        .ext(ExtUser.builder().consent("BONV8oqONXwgmADACHENAO7pqzAAppY").build())
                        .build());
    }

    @Test
    public void shouldKeepEmptyUserWhenGdprConsentIsInvalid() {
        // given
        given(httpRequest.getParam("gdpr_consent")).willReturn("consent-value");

        givenBidRequest(
                builder -> builder
                        .user(User.builder().build())
                        .ext(ExtRequest.empty()),
                Imp.builder().build());

        // when
        final BidRequest result = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(result.getUser())
                .isEqualTo(User.builder().build());
    }

    @Test
    public void shouldReturnAddErrorToAuctionContextWhenPrivacyIsNotValid() {
        // given
        given(httpRequest.getParam("gdpr_consent")).willReturn("consent-value");

        givenBidRequest(
                builder -> builder
                        .user(User.builder().build())
                        .ext(ExtRequest.empty()),
                Imp.builder().build());

        // when
        factory.fromRequest(routingContext, 0L).result();

        // then
        @SuppressWarnings("unchecked") final ArgumentCaptor<List<String>> errorsCaptor = ArgumentCaptor.forClass(
                List.class);
        verify(auctionRequestFactory).toAuctionContext(any(), any(), errorsCaptor.capture(), anyLong(), any());
        assertThat(errorsCaptor.getValue()).contains("Amp request parameter consent_string or gdpr_consent have"
                + " invalid format: consent-value");
    }

    @Test
    public void shouldReturnBidRequestWithExtPrebidDataBiddersUpdatedByFpdResolver() throws JsonProcessingException {
        // given
        given(httpRequest.getParam("targeting"))
                .willReturn(mapper.writeValueAsString(Targeting.of(Arrays.asList("appnexus", "rubicon"), null, null)));

        given(fpdResolver.resolveBidRequestExt(any(), any()))
                .willReturn(ExtRequest.of(ExtRequestPrebid.builder()
                        .data(ExtRequestPrebidData.of(Arrays.asList("appnexus", "rubicon"))).build()));

        givenBidRequest(
                builder -> builder
                        .ext(ExtRequest.empty()),
                Imp.builder().build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        verify(fpdResolver).resolveBidRequestExt(any(), any());
        assertThat(request)
                .extracting(BidRequest::getExt)
                .containsOnly(ExtRequest.of(ExtRequestPrebid.builder()
                        .data(ExtRequestPrebidData.of(Arrays.asList("appnexus", "rubicon"))).build()));
    }

    @Test
    public void shouldReturnBidRequestImpExtContextDataWithTargetingAttributes() throws JsonProcessingException {
        // given
        given(httpRequest.getParam("targeting"))
                .willReturn(mapper.writeValueAsString(Targeting.of(Arrays.asList("appnexus", "rubicon"), null, null)));

        given(fpdResolver.resolveImpExt(any(), any()))
                .willReturn(mapper.createObjectNode().set("context", mapper.createObjectNode()
                        .set("data", mapper.createObjectNode().put("attr1", "value1").put("attr2", "value2"))));

        givenBidRequest(
                builder -> builder
                        .ext(ExtRequest.empty()),
                Imp.builder().build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        verify(fpdResolver).resolveBidRequestExt(any(), any());
        assertThat(singletonList(request))
                .flatExtracting(BidRequest::getImp)
                .containsOnly(Imp.builder().secure(1).ext(mapper.createObjectNode().set("context",
                        mapper.createObjectNode().set("data", mapper.createObjectNode().put("attr1", "value1")
                                .put("attr2", "value2")))).build());
    }

    @Test
    public void shouldReturnBidRequestWithoutRegsExtWhenNoPrivacyPolicyIsExist() {
        // given
        givenBidRequest(
                builder -> builder.ext(ExtRequest.empty()),
                Imp.builder().build());

        // when
        final BidRequest result = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(result.getRegs()).isNull();
    }

    @Test
    public void shouldReturnBidRequestWithRegsExtUsPrivacyWhenUsPrivacyParamIsValid() {
        // given
        given(httpRequest.getParam("gdpr_consent")).willReturn("1N--");

        givenBidRequest(
                builder -> builder.ext(ExtRequest.empty()),
                Imp.builder().build());

        // when
        final BidRequest result = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(result.getRegs())
                .isEqualTo(Regs.of(null, ExtRegs.of(null, "1N--")));
    }

    @Test
    public void shouldReturnBidRequestWithRegsExtUsPrivacyWhenConsentStringIsValid() {
        // given
        given(httpRequest.getParam("consent_string")).willReturn("1Y-N");

        givenBidRequest(
                builder -> builder
                        .user(User.builder().build())
                        .regs(Regs.of(1, ExtRegs.of(1, "replaced")))
                        .ext(ExtRequest.empty()),
                Imp.builder().build());

        // when
        final BidRequest result = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(result.getUser())
                .isEqualTo(User.builder().build());
        assertThat(result.getRegs())
                .isEqualTo(Regs.of(1, ExtRegs.of(1, "1Y-N")));
    }

    @Test
    public void shouldPassExtPrebidDebugFlagIfPresent() {
        // given
        givenBidRequest(
                builder -> builder
                        .ext(ExtRequest.of(ExtRequestPrebid.builder().debug(1).build())),
                Imp.builder().build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getDebug)
                .containsOnly(1);
    }

    private void givenBidRequest(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestBuilderCustomizer,
            Imp... imps) {
        final List<Imp> impList = imps.length > 0 ? asList(imps) : null;

        final BidRequest bidRequest = bidRequestBuilderCustomizer.apply(BidRequest.builder().imp(impList)).build();

        given(storedRequestProcessor.processAmpRequest(anyString())).willReturn(Future.succeededFuture(bidRequest));

        given(auctionRequestFactory.fillImplicitParameters(any(), any(), any())).willAnswer(answerWithFirstArgument());
        given(auctionRequestFactory.validateRequest(any())).willAnswer(answerWithFirstArgument());
        given(auctionRequestFactory.toAuctionContext(any(), any(), anyList(), anyLong(), any()))
                .willAnswer(invocationOnMock -> Future.succeededFuture(
                        AuctionContext.builder()
                                .bidRequest((BidRequest) invocationOnMock.getArguments()[1])
                                .build()));
    }

    private static ExtRequest givenRequestExt(ExtRequestTargeting extRequestTargeting) {
        return ExtRequest.of(ExtRequestPrebid.builder()
                .targeting(extRequestTargeting)
                .build());
    }
}
