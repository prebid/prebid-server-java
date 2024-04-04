package org.prebid.server.auction.requestfactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Dooh;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.User;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.impl.SocketAddressImpl;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.DebugResolver;
import org.prebid.server.auction.FpdResolver;
import org.prebid.server.auction.GeoLocationServiceWrapper;
import org.prebid.server.auction.ImplicitParametersExtractor;
import org.prebid.server.auction.OrtbTypesResolver;
import org.prebid.server.auction.StoredRequestProcessor;
import org.prebid.server.auction.gpp.AmpGppService;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.debug.DebugContext;
import org.prebid.server.auction.privacy.contextfactory.AmpPrivacyContextFactory;
import org.prebid.server.auction.versionconverter.BidRequestOrtbVersionConversionManager;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.metric.MetricName;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.model.Endpoint;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.privacy.ccpa.Ccpa;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.privacy.model.PrivacyContext;
import org.prebid.server.proto.openrtb.ext.ExtIncludeBrandCategory;
import org.prebid.server.proto.openrtb.ext.request.ConsentedProvidersSettings;
import org.prebid.server.proto.openrtb.ext.request.ExtGranularityRange;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidAmp;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCacheBids;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCacheVastxml;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidChannel;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.request.Targeting;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.function.Function.identity;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.prebid.server.assertion.FutureAssertion.assertThat;

public class AmpRequestFactoryTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Ortb2RequestFactory ortb2RequestFactory;
    @Mock
    private StoredRequestProcessor storedRequestProcessor;
    @Mock
    private BidRequestOrtbVersionConversionManager ortbVersionConversionManager;
    @Mock
    private AmpGppService ampGppService;
    @Mock
    private OrtbTypesResolver ortbTypesResolver;
    @Mock
    private ImplicitParametersExtractor implicitParametersExtractor;
    @Mock
    private Ortb2ImplicitParametersResolver ortb2ImplicitParametersResolver;
    @Mock
    private FpdResolver fpdResolver;
    @Mock
    private AmpPrivacyContextFactory ampPrivacyContextFactory;
    @Mock
    private DebugResolver debugResolver;
    @Mock
    private GeoLocationServiceWrapper geoLocationServiceWrapper;

    private AmpRequestFactory target;

    @Mock
    private HttpServerRequest httpRequest;
    @Mock
    private RoutingContext routingContext;

    private BidRequest defaultBidRequest;

    @Before
    public void setUp() {
        defaultBidRequest = BidRequest.builder().build();

        given(ortbVersionConversionManager.convertToAuctionSupportedVersion(any()))
                .willAnswer(invocation -> invocation.getArgument(0));

        given(ampGppService.contextFrom(any())).willReturn(Future.succeededFuture());
        given(ampGppService.updateBidRequest(any(), any()))
                .willAnswer(invocation -> invocation.getArgument(0));

        given(routingContext.request()).willReturn(httpRequest);
        given(routingContext.queryParams()).willReturn(
                MultiMap.caseInsensitiveMultiMap()
                        .add("tag_id", "tagId"));
        given(httpRequest.headers()).willReturn(MultiMap.caseInsensitiveMultiMap());
        given(httpRequest.remoteAddress()).willReturn(new SocketAddressImpl(1234, "host"));

        given(ortb2RequestFactory.createAuctionContext(any(), eq(MetricName.amp))).willReturn(AuctionContext.builder()
                .prebidErrors(new ArrayList<>())
                .build());
        given(ortb2RequestFactory.executeEntrypointHooks(any(), any(), any()))
                .willAnswer(invocation -> toHttpRequest(invocation.getArgument(0), invocation.getArgument(1)));
        given(ortb2RequestFactory.restoreResultFromRejection(any()))
                .willAnswer(invocation -> Future.failedFuture((Throwable) invocation.getArgument(0)));
        given(ortb2RequestFactory.enrichWithPriceFloors(any())).willAnswer(invocation -> invocation.getArgument(0));
        given(ortb2RequestFactory.updateTimeout(any())).willAnswer(invocation -> invocation.getArgument(0));

        given(fpdResolver.resolveApp(any(), any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(fpdResolver.resolveSite(any(), any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(fpdResolver.resolveUser(any(), any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(fpdResolver.resolveImpExt(any(), any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(ortb2RequestFactory.activityInfrastructureFrom(any()))
                .willReturn(Future.succeededFuture());
        given(geoLocationServiceWrapper.lookup(any()))
                .willReturn(Future.succeededFuture(GeoInfo.builder().vendor("vendor").build()));

        given(debugResolver.debugContextFrom(any())).willReturn(DebugContext.of(true, true, null));

        final PrivacyContext defaultPrivacyContext = PrivacyContext.of(
                Privacy.builder()
                        .gdpr("0")
                        .consentString(EMPTY)
                        .ccpa(Ccpa.EMPTY)
                        .coppa(0)
                        .build(),
                TcfContext.empty());
        given(ampPrivacyContextFactory.contextFrom(any()))
                .willReturn(Future.succeededFuture(defaultPrivacyContext));

        target = new AmpRequestFactory(
                ortb2RequestFactory,
                storedRequestProcessor,
                ortbVersionConversionManager,
                ampGppService,
                ortbTypesResolver,
                implicitParametersExtractor,
                ortb2ImplicitParametersResolver,
                fpdResolver,
                ampPrivacyContextFactory,
                debugResolver,
                jacksonMapper,
                geoLocationServiceWrapper);
    }

    @Test
    public void shouldReturnFailedFutureIfRequestHasNoTagId() {
        // given
        routingContext.queryParams().set("tag_id", (String) null);

        // when
        final Future<?> future = target.fromRequest(routingContext, 0L);

        // then
        verifyNoInteractions(storedRequestProcessor);
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
        final Future<?> future = target.fromRequest(routingContext, 0L);

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
        final Future<?> future = target.fromRequest(routingContext, 0L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(InvalidRequestException.class);
        assertThat(((InvalidRequestException) future.cause()).getMessages())
                .hasSize(1).containsOnly("data for tag_id 'tagId' includes 2 imp elements. Only one is allowed");
    }

    @Test
    public void shouldReturnFailedFutureIfStoredBidRequestHasApp() {
        // given
        final Imp imp = Imp.builder().build();
        givenBidRequest(builder -> builder.app(App.builder().build()), imp);

        // when
        final Future<?> future = target.fromRequest(routingContext, 0L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(InvalidRequestException.class);
        assertThat(((InvalidRequestException) future.cause()).getMessages())
                .hasSize(1).containsOnly("request.app must not exist in AMP stored requests.");
    }

    @Test
    public void shouldReturnFailedFutureIfStoredBidRequestHasDooh() {
        // given
        final Imp imp = Imp.builder().build();
        givenBidRequest(builder -> builder.dooh(Dooh.builder().build()), imp);

        // when
        final Future<?> future = target.fromRequest(routingContext, 0L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(InvalidRequestException.class);
        assertThat(((InvalidRequestException) future.cause()).getMessages())
                .hasSize(1).containsOnly("request.dooh must not exist in AMP stored requests.");
    }

    @Test
    public void shouldUseQueryParamsModifiedByEntrypointHooks() {
        // given
        routingContext.queryParams().set("debug", "0");

        doAnswer(invocation -> Future.succeededFuture(HttpRequestContext.builder()
                .queryParams(CaseInsensitiveMultiMap.builder()
                        .add("tag_id", "tagId")
                        .add("debug", "1")
                        .build())
                .build()))
                .when(ortb2RequestFactory)
                .executeEntrypointHooks(any(), any(), any());

        givenBidRequest();

        // when
        final BidRequest request = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .extracting(BidRequest::getExt).isNotNull()
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getDebug)
                .containsExactly(1);
    }

    @Test
    public void shouldReturnFailedFutureIfEntrypointHookRejectedRequest() {
        // given
        givenBidRequest();

        final Throwable exception = new RuntimeException();
        doAnswer(invocation -> Future.failedFuture(exception))
                .when(ortb2RequestFactory)
                .executeEntrypointHooks(any(), any(), any());

        final AuctionContext auctionContext = AuctionContext.builder().requestRejected(true).build();
        doReturn(Future.succeededFuture(auctionContext))
                .when(ortb2RequestFactory)
                .restoreResultFromRejection(eq(exception));

        // when
        final Future<AuctionContext> future = target.fromRequest(routingContext, 0L);

        // then
        assertThat(future).succeededWith(auctionContext);
    }

    @Test
    public void shouldEnrichAuctionContextWithDebugContext() {
        // given
        givenBidRequest();

        // when
        final Future<AuctionContext> result = target.fromRequest(routingContext, 0);

        // then
        verify(debugResolver).debugContextFrom(any());
        assertThat(result.result().getDebugContext())
                .isEqualTo(DebugContext.of(true, true, null));
    }

    @Test
    public void shouldReturnBidRequestWithDefaultPrebidValuesIfPrebidIsNullInStoredRequest() {
        // given
        givenBidRequest();

        // when
        final BidRequest request = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        // result was wrapped to list because extracting method works different on iterable and not iterable objects,
        // which force to make type casting or exception handling in lambdas
        assertThat(singletonList(request))
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .containsExactly(ExtRequestPrebid.builder()
                        .storedrequest(ExtStoredRequest.of("tagId"))
                        .amp(ExtRequestPrebidAmp.of(singletonMap("tag_id", "tagId")))
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
    public void shouldCallOrtbTypeResolver() {
        // given
        givenBidRequest();

        // when
        target.fromRequest(routingContext, 0L).result();

        // then
        verify(ortbTypesResolver).normalizeTargeting(any(), anyList(), any());
    }

    @Test
    public void shouldReturnBidRequestWithDefaultTargetingIfStoredBidRequestExtHasNoTargeting() {
        // given
        givenBidRequest();

        // when
        final BidRequest request = target.fromRequest(routingContext, 0L).result().getBidRequest();

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
        final BidRequest request = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .extracting(BidRequest::getExt).isNotNull()
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getTargeting)
                .extracting(ExtRequestTargeting::getIncludewinners)
                .containsExactly(true);
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
        final BidRequest request = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .extracting(BidRequest::getExt).isNotNull()
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getTargeting)
                .extracting(ExtRequestTargeting::getIncludewinners)
                .containsExactly(false);
    }

    @Test
    public void shouldReturnBidRequestWithIncludeFormatFromStoredBidRequest() {
        // given
        givenBidRequest(
                builder -> builder
                        .ext(givenRequestExt(
                                ExtRequestTargeting.builder()
                                        .pricegranularity(mapper.createObjectNode().put("foo", "bar"))
                                        .includeformat(true)
                                        .build())),
                Imp.builder().build());

        // when
        final BidRequest request = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .extracting(BidRequest::getExt).isNotNull()
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getTargeting)
                .extracting(ExtRequestTargeting::getIncludeformat)
                .containsExactly(true);
    }

    @Test
    public void shouldReturnBidRequestWithDefaultIncludeBidderKeysIfStoredRequestExtTargetingHasNoIncludeBidderKeys() {
        // given
        givenBidRequest(
                builder -> builder.ext(givenRequestExt(ExtRequestTargeting.builder().includewinners(false).build())),
                Imp.builder().build());

        // when
        final BidRequest request = target.fromRequest(routingContext, 0L).result().getBidRequest();

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
        final BidRequest request = target.fromRequest(routingContext, 0L).result().getBidRequest();

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
        final BidRequest request = target.fromRequest(routingContext, 0L).result().getBidRequest();

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

    @Test
    public void shouldReturnBidRequestWithNotChangedExtRequestPrebidTargetingFields() {
        // given
        givenBidRequest(
                builder -> builder
                        .ext(givenRequestExt(ExtRequestTargeting.builder()
                                .includebrandcategory(ExtIncludeBrandCategory.of(1, "publisher", true, false))
                                .truncateattrchars(10)
                                .build())),
                Imp.builder().build());

        // when
        final BidRequest request = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .extracting(BidRequest::getExt).isNotNull()
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getTargeting)
                .extracting(ExtRequestTargeting::getIncludebrandcategory, ExtRequestTargeting::getTruncateattrchars)
                .containsOnly(tuple(ExtIncludeBrandCategory.of(1, "publisher", true, false), 10));
    }

    @Test
    public void shouldReturnBidRequestWithDefaultCachingIfStoredBidRequestExtHasNoCaching() {
        // given
        givenBidRequest();

        // when
        final BidRequest request = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .extracting(BidRequest::getExt).isNotNull()
                .extracting(extBidRequest -> extBidRequest.getPrebid().getCache().getBids())
                .containsExactly(ExtRequestPrebidCacheBids.of(null, null));
    }

    @Test
    public void shouldReturnBidRequestWithChannelFromStoredBidRequest() {
        // given
        givenBidRequest(
                builder -> builder
                        .ext(ExtRequest.of(ExtRequestPrebid.builder()
                                .channel(ExtRequestPrebidChannel.of("custom"))
                                .build())),
                Imp.builder().build());

        // when
        final BidRequest request = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .extracting(BidRequest::getExt)
                .extracting(extBidRequest -> extBidRequest.getPrebid().getChannel())
                .containsExactly(ExtRequestPrebidChannel.of("custom"));
    }

    @Test
    public void shouldReturnBidRequestWithImpSecureEqualsToOneIfInitiallyItWasNotSecured() {
        // given
        givenBidRequest();

        // when
        final BidRequest request = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getSecure)
                .containsExactly(1);
    }

    @Test
    public void shouldReturnBidRequestWithExtPrebidDebugOneWhenDebugQueryParamIsOne() {
        // given
        routingContext.queryParams().add("debug", "1");

        givenBidRequest();

        // when
        final BidRequest request = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getDebug)
                .containsOnly(1);
    }

    @Test
    public void shouldReturnBidRequestWithExtPrebidDebugZeroWhenDebugQueryParamIsZero() {
        // given
        routingContext.queryParams().add("debug", "0");

        givenBidRequest();

        // when
        final BidRequest request = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getDebug)
                .containsOnly(0);
    }

    @Test
    public void shouldReturnBidRequestWithoutExtPrebidDebugWhenDebugQueryParamIsNotValid() {
        // given
        routingContext.queryParams().add("debug", "3");

        givenBidRequest();

        // when
        final BidRequest request = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getDebug)
                .containsNull();
    }

    @Test
    public void shouldReturnBidRequestWithOverriddenTagIdBySlotParamValue() {
        // given
        routingContext.queryParams().add("slot", "Overridden-tagId");

        givenBidRequest();

        // when
        final BidRequest request = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getTagid)
                .containsOnly("Overridden-tagId");
    }

    @Test
    public void shouldSetBidRequestSiteExt() {
        // given
        routingContext.queryParams().add("curl", "");

        givenBidRequest();

        // when
        final BidRequest request = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .extracting(BidRequest::getSite)
                .extracting(Site::getExt)
                .containsOnly(ExtSite.of(1, null));
    }

    @Test
    public void shouldReturnBidRequestWithSitePageAndDomainFromCurlQueryParam() {
        // given
        routingContext.queryParams().add("curl", "http://overridden.site.page:8080/path");

        givenBidRequest();

        // when
        final BidRequest request = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .extracting(BidRequest::getSite)
                .extracting(Site::getPage, Site::getDomain, Site::getExt)
                .containsOnly(tuple(
                        "http://overridden.site.page:8080/path", "overridden.site.page", ExtSite.of(1, null)));
    }

    @Test
    public void shouldReturnBidRequestWithSitePublisherIdFromAccountQueryParam() {
        // given
        routingContext.queryParams().add("account", "accountId");

        givenBidRequest();

        // when
        final BidRequest request = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .extracting(BidRequest::getSite)
                .extracting(Site::getPublisher, Site::getExt)
                .containsOnly(tuple(
                        Publisher.builder().id("accountId").build(),
                        ExtSite.of(1, null)));
    }

    @Test
    public void shouldReturnRequestWithOverriddenBannerFormatByOverwriteWHParamsRespectingThemOverWH() {
        // given
        routingContext.queryParams().add("w", "10");
        routingContext.queryParams().add("ow", "1000");
        routingContext.queryParams().add("h", "20");
        routingContext.queryParams().add("oh", "2000");
        routingContext.queryParams().add("ms", "44x88,66x99");

        givenBidRequest(
                identity(),
                Imp.builder()
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder()
                                        .w(1)
                                        .h(2)
                                        .build()))
                                .build()).build());

        // when
        final BidRequest request = target.fromRequest(routingContext, 0L).result().getBidRequest();

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
        routingContext.queryParams().add("ow", "10");
        routingContext.queryParams().add("w", "30");
        routingContext.queryParams().add("h", "40");
        routingContext.queryParams().add("ms", "50x60");

        givenBidRequest(
                identity(),
                Imp.builder()
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder()
                                        .w(1)
                                        .h(2)
                                        .build()))
                                .build()).build());

        // when
        final BidRequest request = target.fromRequest(routingContext, 0L).result().getBidRequest();

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
        routingContext.queryParams().add("oh", "20");
        routingContext.queryParams().add("w", "30");
        routingContext.queryParams().add("h", "40");
        routingContext.queryParams().add("ms", "50x60");

        givenBidRequest(
                identity(),
                Imp.builder()
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder()
                                        .w(1)
                                        .h(2)
                                        .build()))
                                .build()).build());

        // when
        final BidRequest request = target.fromRequest(routingContext, 0L).result().getBidRequest();

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
        routingContext.queryParams().add("w", "30");
        routingContext.queryParams().add("h", "40");
        routingContext.queryParams().add("ms", "50x60");

        givenBidRequest(
                identity(),
                Imp.builder()
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder()
                                        .w(1)
                                        .h(2)
                                        .build()))
                                .build()).build());

        // when
        final BidRequest request = target.fromRequest(routingContext, 0L).result().getBidRequest();

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
        routingContext.queryParams().add("w", "30");
        routingContext.queryParams().add("h", "40");

        givenBidRequest(
                identity(),
                Imp.builder()
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder()
                                        .w(1)
                                        .h(2)
                                        .build()))
                                .build()).build());

        // when
        final BidRequest request = target.fromRequest(routingContext, 0L).result().getBidRequest();

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
        routingContext.queryParams().add("w", "30");

        givenBidRequest(
                identity(),
                Imp.builder()
                        .banner(Banner.builder()
                                .format(asList(Format.builder().w(1).h(2).build(),
                                        Format.builder().w(3).h(4).build()))
                                .build()).build());

        // when
        final BidRequest request = target.fromRequest(routingContext, 0L).result().getBidRequest();

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
        routingContext.queryParams().add("h", "40");

        givenBidRequest(
                identity(),
                Imp.builder()
                        .banner(Banner.builder()
                                .format(asList(Format.builder().w(1).h(2).build(),
                                        Format.builder().w(3).h(4).build()))
                                .build()).build());

        // when
        final BidRequest request = target.fromRequest(routingContext, 0L).result().getBidRequest();

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
        routingContext.queryParams().add("ms", "44x88,66x99");

        givenBidRequest(
                identity(),
                Imp.builder()
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder()
                                        .w(1)
                                        .h(2)
                                        .build()))
                                .build()).build());

        // when
        final BidRequest request = target.fromRequest(routingContext, 0L).result().getBidRequest();

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
        routingContext.queryParams().add("ms", ",");

        givenBidRequest(
                identity(),
                Imp.builder()
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder()
                                        .w(1)
                                        .h(2)
                                        .build()))
                                .build()).build());

        // when
        final BidRequest request = target.fromRequest(routingContext, 0L).result().getBidRequest();

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
        routingContext.queryParams().add("ms", ",33x,44x77,abc,");

        givenBidRequest(
                identity(),
                Imp.builder()
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder()
                                        .w(1)
                                        .h(2)
                                        .build()))
                                .build()).build());

        // when
        final BidRequest request = target.fromRequest(routingContext, 0L).result().getBidRequest();

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
        routingContext.queryParams().add("ms", "900xZ");

        givenBidRequest(
                identity(),
                Imp.builder()
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder()
                                        .w(1)
                                        .h(2)
                                        .build()))
                                .build()).build());

        // when
        final BidRequest request = target.fromRequest(routingContext, 0L).result().getBidRequest();

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
        routingContext.queryParams().add("ms", "44x77, 0x0");

        givenBidRequest(
                identity(),
                Imp.builder()
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder()
                                        .w(1)
                                        .h(2)
                                        .build()))
                                .build()).build());

        // when
        final BidRequest request = target.fromRequest(routingContext, 0L).result().getBidRequest();

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
        routingContext.queryParams().add("ms", "33x,44x77");

        givenBidRequest(
                identity(),
                Imp.builder()
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder()
                                        .w(1)
                                        .h(2)
                                        .build()))
                                .build()).build());

        // when
        final BidRequest request = target.fromRequest(routingContext, 0L).result().getBidRequest();

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
        routingContext.queryParams().add("ow", "0");
        routingContext.queryParams().add("oh", "0");
        routingContext.queryParams().add("w", "0");
        routingContext.queryParams().add("h", "0");
        routingContext.queryParams().add("ms", "0x0");

        givenBidRequest(
                identity(),
                Imp.builder()
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder()
                                        .w(1)
                                        .h(2)
                                        .build()))
                                .build()).build());

        // when
        final BidRequest request = target.fromRequest(routingContext, 0L).result().getBidRequest();

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
        routingContext.queryParams().add("ow", "100");
        routingContext.queryParams().add("oh", "invalid");
        routingContext.queryParams().add("h", "200");

        givenBidRequest(
                identity(),
                Imp.builder()
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder()
                                        .w(1)
                                        .h(2)
                                        .build()))
                                .build()).build());

        // when
        final BidRequest request = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .flatExtracting(Banner::getFormat)
                .extracting(Format::getW, Format::getH)
                .containsOnly(tuple(100, 200));
    }

    @Test
    public void shouldReturnBidRequestWithTmaxFromTimeoutQueryParam() {
        // given
        routingContext.queryParams().add("timeout", "1000");

        givenBidRequest();

        // when
        final BidRequest request = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(request.getTmax()).isEqualTo(1000L);
    }

    @Test
    public void shouldReturnBidRequestWithoutUserExtConsentWhenConsentAndAddtlConsentAreAbsent() {
        // given
        givenBidRequest();

        // when
        final BidRequest result = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(result.getUser()).isNull();
    }

    @Test
    public void shouldReturnBidRequestWithoutUserExtConsentWhenConsentTypeIsNotTcfAndAddtlConsentIsAbsent() {
        // given
        routingContext.queryParams()
                .add("consent_type", "3")
                .add("consent_string", "consent_string");

        givenBidRequest();

        // when
        final BidRequest result = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(result.getUser()).isNull();
    }

    @Test
    public void shouldReturnBidRequestWithUserExtConsentWhenGdprConsentIsValidAndConsentTypeIsNotPresent() {
        // given
        routingContext.queryParams().add("gdpr_consent", "BONV8oqONXwgmADACHENAO7pqzAAppY");

        givenBidRequest();

        // when
        final BidRequest result = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(result.getUser())
                .isEqualTo(User.builder()
                        .consent("BONV8oqONXwgmADACHENAO7pqzAAppY")
                        .build());
    }

    @Test
    public void shouldReturnBidRequestWithUserExtConsentWhenGdprConsentIsValidAndConsentTypeIsTCFV2() {
        // given
        routingContext.queryParams()
                .add("gdpr_consent", "BONV8oqONXwgmADACHENAO7pqzAAppY")
                .add("consent_type", "2");

        givenBidRequest();

        // when
        final BidRequest result = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(result.getUser())
                .isEqualTo(User.builder()
                        .consent("BONV8oqONXwgmADACHENAO7pqzAAppY")
                        .build());
    }

    @Test
    public void shouldReturnBidRequestWithProvidersSettingsContainsAddtlConsentIfParamIsPresent() {
        // given
        routingContext.queryParams()
                .add("addtl_consent", "someConsent");

        givenBidRequest();

        // when
        final BidRequest result = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(result.getUser())
                .isEqualTo(User.builder()
                        .ext(ExtUser.builder()
                                .consentedProvidersSettings(ConsentedProvidersSettings.of("someConsent"))
                                .build())
                        .build());
    }

    @Test
    public void shouldReturnBidRequestImpExtContextDataWithTargetingAttributes() throws JsonProcessingException {
        // given
        routingContext.queryParams()
                .add("targeting", mapper.writeValueAsString(
                        Targeting.of(Arrays.asList("appnexus", "rubicon"), null, null)));

        given(fpdResolver.resolveImpExt(any(), any()))
                .willReturn(mapper.createObjectNode().set("context", mapper.createObjectNode()
                        .set("data", mapper.createObjectNode().put("attr1", "value1").put("attr2", "value2"))));

        givenBidRequest();

        // when
        final BidRequest request = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .flatExtracting(BidRequest::getImp)
                .containsOnly(Imp.builder().secure(1).ext(mapper.createObjectNode().set("context",
                        mapper.createObjectNode().set("data", mapper.createObjectNode().put("attr1", "value1")
                                .put("attr2", "value2")))).build());
    }

    @Test
    public void shouldThrowInvalidRequestExceptionWhenTargetingHasTypeOtherToObject() {
        // given
        routingContext.queryParams().add("targeting", "[\"a\"]");

        givenBidRequest();

        // when
        final Future<AuctionContext> result = target.fromRequest(routingContext, 0L);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Error decoding targeting, expected type is `object` but was ARRAY");
    }

    @Test
    public void shouldReturnBidRequestWithRegsContainsGdprEqualOneIfGdprAppliesIsTrue() {
        // given
        routingContext.queryParams().add("gdpr_applies", "true");

        givenBidRequest();

        // when
        final BidRequest result = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(result.getRegs())
                .isEqualTo(Regs.builder().gdpr(1).build());
    }

    @Test
    public void shouldNotAddErrorToAuctionContextIfNoConsentParamProvided() {
        // given
        givenBidRequest();

        // when
        final AuctionContext result = target.fromRequest(routingContext, 0L).result();

        // then
        assertThat(result.getPrebidErrors()).isEmpty();
    }

    @Test
    public void shouldAddErrorToAuctionContextWhenConsentTypeIsInvalid() {
        // given
        routingContext.queryParams().add("consent_type", "invalid");

        givenBidRequest();

        // when
        final AuctionContext result = target.fromRequest(routingContext, 0L).result();

        // then
        assertThat(result.getPrebidErrors())
                .containsExactly("Invalid consent_type param passed");
    }

    @Test
    public void shouldAddErrorToAuctionContextWhenConsentStringQueryParamIsInvalid() {
        // given
        routingContext.queryParams().add("consent_string", "consent-value");

        givenBidRequest();

        // when
        final AuctionContext result = target.fromRequest(routingContext, 0L).result();

        // then
        assertThat(result.getPrebidErrors())
                .containsExactly("Amp request parameter consent_string has invalid format: consent-value");
    }

    @Test
    public void shouldAddErrorToAuctionContextWhenGdprConsentQueryParamIsInvalid() {
        // given
        routingContext.queryParams().add("gdpr_consent", "consent-value");

        givenBidRequest();

        // when
        final AuctionContext result = target.fromRequest(routingContext, 0L).result();

        // then
        assertThat(result.getPrebidErrors())
                .containsExactly("Amp request parameter gdpr_consent has invalid format: consent-value");
    }

    @Test
    public void shouldReturnBidRequestWithRegsContainsGdprEqualZeroIfGdprAppliesIsFalse() {
        // given
        routingContext.queryParams().add("gdpr_applies", "false");

        givenBidRequest();

        // when
        final BidRequest result = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(result.getRegs())
                .isEqualTo(Regs.builder().gdpr(0).build());
    }

    @Test
    public void shouldReturnBidRequestWithRegsExtUsPrivacyWhenGdprConsentQueryParamIsValidUsPrivacyString() {
        // given
        routingContext.queryParams().add("gdpr_consent", "1N--");

        givenBidRequest();

        // when
        final BidRequest result = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(result.getRegs())
                .isEqualTo(Regs.builder().usPrivacy("1N--").build());
    }

    @Test
    public void shouldReturnBidRequestWithRegsExtUsPrivacyWhenConsentTypeIsNotPresent() {
        // given
        routingContext.queryParams().add("consent_string", "1Y-N");

        givenBidRequest();

        // when
        final BidRequest result = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(result.getRegs())
                .isEqualTo(Regs.builder().usPrivacy("1Y-N").build());
    }

    @Test
    public void shouldReturnBidRequestWithRegsExtUsPrivacyWhenConsentStringIsValidAndConsentTypeIsUsPrivacy() {
        // given
        routingContext.queryParams()
                .add("consent_string", "1Y-N")
                .add("consent_type", "3");

        givenBidRequest();

        // when
        final BidRequest result = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(result.getRegs())
                .isEqualTo(Regs.builder().usPrivacy("1Y-N").build());
    }

    @Test
    public void shouldReturnBidRequestWithRegsGppWhenConsentStringIsPresentAndConsentTypeIsGpp() {
        // given
        routingContext.queryParams()
                .add("consent_string", "someGppString")
                .add("consent_type", "4");

        givenBidRequest();

        // when
        final BidRequest result = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(result.getRegs())
                .isEqualTo(Regs.builder().gpp("someGppString").build());
    }

    @Test
    public void shouldReturnBidRequestWithRegsGppSidWhenGppSidParameterPresentAndCanBeParsed() {
        // given
        routingContext.queryParams().add("gpp_sid", "1,2,3");

        givenBidRequest();

        // when
        final BidRequest result = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(result.getRegs())
                .isEqualTo(Regs.builder().gppSid(List.of(1, 2, 3)).build());
    }

    @Test
    public void shouldPopulateRegsObjectWithGppDataIfGppSidCouldBeParsed() {
        // given
        routingContext.queryParams()
                .add("consent_string", "someGppString")
                .add("consent_type", "4")
                .add("gpp_sid", "1,2,3");

        givenBidRequest();

        // when
        final BidRequest result = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(result.getRegs())
                .isEqualTo(Regs.builder()
                        .gpp("someGppString")
                        .gppSid(List.of(1, 2, 3))
                        .build());
    }

    @Test
    public void shouldReturnBidRequestWithGpc() {
        // given
        given(implicitParametersExtractor.gpcFrom(any())).willReturn("1");

        givenBidRequest();

        // when
        final BidRequest result = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(result.getRegs()).isEqualTo(Regs.builder()
                .ext(ExtRegs.of(null, null, "1", null))
                .build());
    }

    @Test
    public void shouldNotPopulateRegsObjectWithGppDataIfGppSidCouldNotBeParsed() {
        // given
        routingContext.queryParams()
                .add("consent_string", "someGppString")
                .add("consent_type", "4")
                .add("gpp_sid", "1,2,ab");

        givenBidRequest();

        // when
        final BidRequest result = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(result.getRegs()).isNull();
    }

    @Test
    public void shouldReturnBidRequestWithCreatedExtPrebidAmpData() {
        // given
        routingContext.queryParams()
                .add("queryParam1", "value1")
                .add("queryParam2", "value2");

        givenBidRequest();

        // when
        final BidRequest result = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        final Map<String, String> expectedAmpData = new HashMap<>();
        expectedAmpData.put("queryParam1", "value1");
        expectedAmpData.put("queryParam2", "value2");
        expectedAmpData.put("tag_id", "tagId");
        assertThat(singletonList(result))
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getAmp)
                .extracting(ExtRequestPrebidAmp::getData)
                .containsOnly(expectedAmpData);
    }

    @Test
    public void shouldReturnModifiedBidRequestWhenRequestWasPopulatedWithImplicitParams() {
        // given
        givenBidRequest();

        final BidRequest updatedBidRequest = defaultBidRequest.toBuilder().id("updated").build();
        given(ortb2ImplicitParametersResolver.resolve(any(), any(), any(), anyBoolean()))
                .willReturn(updatedBidRequest);

        // when
        final AuctionContext result = target.fromRequest(routingContext, 0L).result();

        // then
        assertThat(result.getBidRequest()).isEqualTo(updatedBidRequest);
    }

    @Test
    public void shouldReturnPopulatedPrivacyContextAndGeoWhenPrivacyEnforcementReturnContext() {
        // given
        givenBidRequest();

        final GeoInfo geoInfo = GeoInfo.builder().vendor("vendor").city("found").build();
        final PrivacyContext privacyContext = PrivacyContext.of(
                Privacy.builder()
                        .gdpr("1")
                        .consentString("consent")
                        .ccpa(Ccpa.EMPTY)
                        .coppa(0)
                        .build(),
                TcfContext.builder().geoInfo(geoInfo).build());

        given(ampPrivacyContextFactory.contextFrom(any())).willReturn(Future.succeededFuture(privacyContext));

        // when
        final AuctionContext result = target.fromRequest(routingContext, 0L).result();

        // then
        assertThat(result.getPrivacyContext()).isEqualTo(privacyContext);
        assertThat(result.getGeoInfo()).isEqualTo(geoInfo);
    }

    @Test
    public void shouldPassAmpEndpointAndRequestMetricType() {
        // given
        givenBidRequest();

        // when
        target.fromRequest(routingContext, 0L);

        // then
        verify(ortb2RequestFactory).createAuctionContext(eq(Endpoint.openrtb2_amp), eq(MetricName.amp));
    }

    @Test
    public void shouldUseBidRequestModifiedByProcessedAuctionRequestHooks() {
        // given
        givenBidRequest(
                builder -> builder
                        .site(Site.builder().domain("example.com").build())
                        .ext(ExtRequest.empty()),
                Imp.builder().build());

        final BidRequest modifiedBidRequest = BidRequest.builder()
                .app(App.builder().bundle("org.company.application").build())
                .build();
        doAnswer(invocation -> Future.succeededFuture(modifiedBidRequest))
                .when(ortb2RequestFactory)
                .executeProcessedAuctionRequestHooks(any());

        // when
        final Future<AuctionContext> result = target.fromRequest(routingContext, 0L);

        // then
        final BidRequest resultBidRequest = result.result().getBidRequest();
        assertThat(resultBidRequest.getSite()).isNull();
        assertThat(resultBidRequest.getApp()).isEqualTo(App.builder().bundle("org.company.application").build());
    }

    @Test
    public void shouldReturnFailedFutureIfProcessedAuctionRequestHookRejectedRequest() {
        // given
        givenBidRequest();

        final Throwable exception = new RuntimeException();
        doAnswer(invocation -> Future.failedFuture(exception))
                .when(ortb2RequestFactory)
                .executeProcessedAuctionRequestHooks(any());

        final AuctionContext auctionContext = AuctionContext.builder().requestRejected(true).build();
        doReturn(Future.succeededFuture(auctionContext))
                .when(ortb2RequestFactory)
                .restoreResultFromRejection(eq(exception));

        // when
        final Future<AuctionContext> future = target.fromRequest(routingContext, 0L);

        // then
        assertThat(future).succeededWith(auctionContext);
    }

    @Test
    public void shouldConvertBidRequestToInternalOpenRTBVersion() {
        // given
        givenBidRequest();

        given(ortbVersionConversionManager.convertToAuctionSupportedVersion(any())).willAnswer(
                invocation -> ((BidRequest) invocation.getArgument(0))
                        .toBuilder()
                        .source(Source.builder().tid("uniqTid").build())
                        .build());

        // when
        final Future<AuctionContext> future = target.fromRequest(routingContext, 0L);

        // then
        assertThat(future).isSucceeded();
        assertThat(future.result())
                .extracting(AuctionContext::getBidRequest)
                .extracting(BidRequest::getSource)
                .isEqualTo(Source.builder().tid("uniqTid").build());
    }

    @Test
    public void shouldUpdateTimeout() {
        // given
        givenBidRequest();

        given(ortb2RequestFactory.updateTimeout(any()))
                .willAnswer(invocation -> {
                    final AuctionContext auctionContext = invocation.getArgument(0);
                    return auctionContext.with(auctionContext.getBidRequest().toBuilder().tmax(10000L).build());
                });

        // when
        final Future<AuctionContext> future = target.fromRequest(routingContext, 0L);

        // then
        assertThat(future).isSucceeded();
        assertThat(future.result())
                .extracting(AuctionContext::getBidRequest)
                .extracting(BidRequest::getTmax)
                .isEqualTo(10000L);
    }

    private void givenBidRequest(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> storedBidRequestBuilderCustomizer,
            Imp... imps) {
        final List<Imp> impList = imps.length > 0 ? asList(imps) : null;

        given(storedRequestProcessor.processAmpRequest(any(), anyString(), any()))
                .willAnswer(invocation -> {
                    final BidRequest argument = invocation.getArgument(2);
                    return Future.succeededFuture(
                            storedBidRequestBuilderCustomizer.apply(argument.toBuilder().imp(impList)).build());
                });

        given(ortb2RequestFactory.enrichAuctionContext(any(), any(), any(), anyLong()))
                .willAnswer(invocationOnMock -> ((AuctionContext) invocationOnMock.getArguments()[0]).toBuilder()
                        .httpRequest((HttpRequestContext) invocationOnMock.getArguments()[1])
                        .bidRequest((BidRequest) invocationOnMock.getArguments()[2])
                        .build());
        given(ortb2RequestFactory.fetchAccount(any())).willReturn(Future.succeededFuture());

        given(ortb2ImplicitParametersResolver.resolve(any(), any(), any(), anyBoolean())).willAnswer(
                answerWithFirstArgument());
        given(ortb2RequestFactory.validateRequest(any(), any(), any()))
                .willAnswer(invocation -> Future.succeededFuture((BidRequest) invocation.getArgument(0)));

        given(ortb2RequestFactory.enrichBidRequestWithAccountAndPrivacyData(any()))
                .willAnswer(invocation -> Future.succeededFuture(((AuctionContext) invocation.getArgument(0))
                        .getBidRequest()));
        given(ortb2RequestFactory.enrichBidRequestWithGeolocationData(any()))
                .willAnswer(invocation -> Future.succeededFuture(((AuctionContext) invocation.getArgument(0))
                        .getBidRequest()));
        given(ortb2RequestFactory.executeProcessedAuctionRequestHooks(any()))
                .willAnswer(invocation -> Future.succeededFuture(
                        ((AuctionContext) invocation.getArgument(0)).getBidRequest()));
    }

    private void givenBidRequest() {
        givenBidRequest(identity(), Imp.builder().build());
    }

    private Answer<Object> answerWithFirstArgument() {
        return invocationOnMock -> invocationOnMock.getArguments()[0];
    }

    private static Future<HttpRequestContext> toHttpRequest(RoutingContext routingContext, String body) {
        return Future.succeededFuture(HttpRequestContext.builder()
                .absoluteUri(routingContext.request().absoluteURI())
                .queryParams(toCaseInsensitiveMultiMap(routingContext.queryParams()))
                .headers(toCaseInsensitiveMultiMap(routingContext.request().headers()))
                .body(body)
                .scheme(routingContext.request().scheme())
                .remoteHost(routingContext.request().remoteAddress().host())
                .build());
    }

    private static CaseInsensitiveMultiMap toCaseInsensitiveMultiMap(MultiMap originalMap) {
        final CaseInsensitiveMultiMap.Builder mapBuilder = CaseInsensitiveMultiMap.builder();
        originalMap.entries().forEach(entry -> mapBuilder.add(entry.getKey(), entry.getValue()));

        return mapBuilder.build();
    }

    private static ExtRequest givenRequestExt(ExtRequestTargeting extRequestTargeting) {
        return ExtRequest.of(ExtRequestPrebid.builder()
                .targeting(extRequestTargeting)
                .build());
    }
}
