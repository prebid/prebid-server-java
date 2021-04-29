package org.prebid.server.auction.requestfactory;

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
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.impl.SocketAddressImpl;
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
import org.prebid.server.auction.FpdResolver;
import org.prebid.server.auction.ImplicitParametersExtractor;
import org.prebid.server.auction.OrtbTypesResolver;
import org.prebid.server.auction.PrivacyEnforcementService;
import org.prebid.server.auction.StoredRequestProcessor;
import org.prebid.server.auction.TimeoutResolver;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.RejectedRequestException;
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.metric.MetricName;
import org.prebid.server.model.Endpoint;
import org.prebid.server.model.HttpRequestWrapper;
import org.prebid.server.privacy.ccpa.Ccpa;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.privacy.model.PrivacyContext;
import org.prebid.server.proto.openrtb.ext.ExtIncludeBrandCategory;
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
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidData;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.request.Targeting;

import java.math.BigDecimal;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public class AmpRequestFactoryTest extends VertxTest {

    private static final String ACCOUNT_ID = "acc_id";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private StoredRequestProcessor storedRequestProcessor;
    @Mock
    private Ortb2RequestFactory ortb2RequestFactory;
    @Mock
    private OrtbTypesResolver ortbTypesResolver;
    @Mock
    private ImplicitParametersExtractor implicitParametersExtractor;
    @Mock
    private Ortb2ImplicitParametersResolver ortb2ImplicitParametersResolver;
    @Mock
    private FpdResolver fpdResolver;
    @Mock
    private PrivacyEnforcementService privacyEnforcementService;
    @Mock
    private TimeoutResolver timeoutResolver;

    private AmpRequestFactory target;

    @Mock
    private HttpServerRequest httpRequest;
    @Mock
    private RoutingContext routingContext;

    private BidRequest defaultBidRequest;

    @Before
    public void setUp() {
        defaultBidRequest = BidRequest.builder().build();

        given(timeoutResolver.resolve(any())).willReturn(2000L);
        given(timeoutResolver.adjustTimeout(anyLong())).willReturn(1900L);

        given(httpRequest.remoteAddress()).willReturn(new SocketAddressImpl(1234, "host"));
        given(routingContext.request()).willReturn(httpRequest);
        given(routingContext.queryParams()).willReturn(
                MultiMap.caseInsensitiveMultiMap()
                        .add("tag_id", "tagId"));

        given(ortb2RequestFactory.executeEntrypointHooks(any(), any(), any()))
                .willAnswer(invocation -> toHttpRequest(invocation.getArgument(0), invocation.getArgument(1)));

        given(fpdResolver.resolveApp(any(), any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(fpdResolver.resolveSite(any(), any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(fpdResolver.resolveUser(any(), any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(fpdResolver.resolveImpExt(any(), any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(fpdResolver.resolveBidRequestExt(any(), any())).willAnswer(invocationOnMock -> invocationOnMock
                .getArgument(0));

        final PrivacyContext defaultPrivacyContext = PrivacyContext.of(
                Privacy.of("0", EMPTY, Ccpa.EMPTY, 0),
                TcfContext.empty());
        given(privacyEnforcementService.contextFromBidRequest(any()))
                .willReturn(Future.succeededFuture(defaultPrivacyContext));

        target = new AmpRequestFactory(
                storedRequestProcessor,
                ortb2RequestFactory,
                ortbTypesResolver,
                implicitParametersExtractor,
                ortb2ImplicitParametersResolver,
                fpdResolver,
                privacyEnforcementService,
                timeoutResolver,
                jacksonMapper);
    }

    @Test
    public void shouldReturnFailedFutureIfRequestHasNoTagId() {
        // given
        routingContext.queryParams().set("tag_id", (String) null);

        // when
        final Future<?> future = target.fromRequest(routingContext, 0L);

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
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().build())
                .imp(singletonList(Imp.builder().build()))
                .build();
        given(storedRequestProcessor.processAmpRequest(any(), anyString()))
                .willReturn(Future.succeededFuture(bidRequest));

        // when
        final Future<?> future = target.fromRequest(routingContext, 0L);

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
        final Future<?> future = target.fromRequest(routingContext, 0L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(InvalidRequestException.class);
        assertThat(((InvalidRequestException) future.cause()).getMessages())
                .hasSize(1).containsOnly("AMP requests require Ext to be set");
    }

    @Test
    public void shouldUseQueryParamsModifiedByEntrypointHooks() {
        // given
        doAnswer(invocation -> Future.succeededFuture(HttpRequestWrapper.builder()
                .queryParams(MultiMap.caseInsensitiveMultiMap()
                        .add("tag_id", "tagId")
                        .add("debug", "1"))
                .build()))
                .when(ortb2RequestFactory)
                .executeEntrypointHooks(any(), any(), any());

        givenBidRequest(
                builder -> builder
                        .ext(ExtRequest.of(ExtRequestPrebid.builder().debug(0).build())),
                Imp.builder().build());

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
    public void shouldReturnBidRequestWithDefaultPrebidValuesIfPrebidIsNull() {
        // given
        givenBidRequest(builder -> builder.ext(ExtRequest.empty()), Imp.builder().build());

        // when
        final BidRequest request = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        // result was wrapped to list because extracting method works different on iterable and not iterable objects,
        // which force to make type casting or exception handling in lambdas
        assertThat(singletonList(request))
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .containsExactly(ExtRequestPrebid.builder()
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
                        .channel(ExtRequestPrebidChannel.of("amp"))
                        .build());
    }

    @Test
    public void shouldCallOrtbTypeResolver() {
        // given
        givenBidRequest(
                builder -> builder
                        .ext(givenRequestExt(null)),
                Imp.builder().build());

        // when
        target.fromRequest(routingContext, 0L).result();

        // then
        verify(ortbTypesResolver).normalizeTargeting(any(), anyList(), any());
    }

    @Test
    public void shouldReturnBidRequestWithDefaultTargetingIfStoredBidRequestExtHasNoTargeting() {
        // given
        givenBidRequest(
                builder -> builder
                        .ext(givenRequestExt(null)),
                Imp.builder().build());

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
                                .includebrandcategory(ExtIncludeBrandCategory.of(1, "publisher", true))
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
                .containsOnly(tuple(ExtIncludeBrandCategory.of(1, "publisher", true), 10));
    }

    @Test
    public void shouldReturnBidRequestWithDefaultCachingIfStoredBidRequestExtHasNoCaching() {
        // given
        givenBidRequest(
                builder -> builder
                        .ext(givenRequestExt(null)),
                Imp.builder().build());

        // when
        final BidRequest request = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .extracting(BidRequest::getExt).isNotNull()
                .extracting(extBidRequest -> extBidRequest.getPrebid().getCache().getBids())
                .containsExactly(ExtRequestPrebidCacheBids.of(null, null));
    }

    @Test
    public void shouldReturnBidRequestWithChannelIfStoredBidRequestExtHasNoChannel() {
        // given
        givenBidRequest(
                builder -> builder
                        .ext(givenRequestExt(null)),
                Imp.builder().build());

        // when
        final BidRequest request = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .extracting(BidRequest::getExt)
                .extracting(extBidRequest -> extBidRequest.getPrebid().getChannel())
                .containsExactly(ExtRequestPrebidChannel.of("amp"));
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
        givenBidRequest(
                builder -> builder
                        .ext(givenRequestExt(null)),
                Imp.builder().build());

        // when
        final BidRequest request = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getSecure)
                .containsExactly(1);
    }

    @Test
    public void shouldRespondWithBidRequestWithTestFlagOn() {
        // given
        routingContext.queryParams().add("debug", "1");

        givenBidRequest(
                builder -> builder
                        .test(0)
                        .ext(ExtRequest.empty()),
                Imp.builder().build());

        // when
        target.fromRequest(routingContext, 0L);

        // then
        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(ortb2ImplicitParametersResolver).resolve(captor.capture(), any(), any());

        assertThat(captor.getValue().getTest()).isEqualTo(1);
    }

    @Test
    public void shouldRespondWithBidRequestWithDebugFlagOn() {
        // given
        routingContext.queryParams().add("debug", "1");

        givenBidRequest(
                builder -> builder
                        .ext(ExtRequest.of(ExtRequestPrebid.builder().debug(0).build())),
                Imp.builder().build());

        // when
        target.fromRequest(routingContext, 0L);

        // then
        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(ortb2ImplicitParametersResolver).resolve(captor.capture(), any(), any());

        final ExtRequest extRequest = captor.getValue().getExt();
        assertThat(extRequest.getPrebid().getDebug()).isEqualTo(1);
    }

    @Test
    public void shouldReturnBidRequestWithOverriddenTagIdBySlotParamValue() {
        // given
        routingContext.queryParams().add("slot", "Overridden-tagId");

        givenBidRequest(
                builder -> builder
                        .ext(givenRequestExt(null)),
                Imp.builder().build());

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

        givenBidRequest(
                builder -> builder
                        .ext(ExtRequest.empty()),
                Imp.builder().build());

        // when
        final BidRequest request = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .extracting(BidRequest::getSite)
                .extracting(Site::getExt)
                .containsOnly(ExtSite.of(1, null));
    }

    @Test
    public void shouldReturnBidRequestWithOverriddenSitePageAndDomainByCurlParamValue() {
        // given
        routingContext.queryParams().add("curl", "http://overridden.site.page:8080/path");

        givenBidRequest(
                builder -> builder
                        .ext(ExtRequest.empty())
                        .site(Site.builder().page("http://will.be.overridden/path").build()),
                Imp.builder().build());

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
    public void shouldReturnBidRequestWithSitePageAndDomainContainingCurlParamValueWhenSiteNotInRequest() {
        // given
        routingContext.queryParams().add("curl", "http://overridden.site.page:8080/path");

        givenBidRequest(
                builder -> builder
                        .ext(ExtRequest.empty())
                        .site(null),
                Imp.builder().build());

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
    public void shouldReturnBidRequestWithSitePublisherIdOverriddenWithAccountParamValue() {
        // given
        routingContext.queryParams().add("account", "accountId");

        givenBidRequest(
                builder -> builder
                        .ext(ExtRequest.empty())
                        .site(Site.builder().publisher(Publisher.builder().id("will-be-overridden").build()).build()),
                Imp.builder().build());

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
    public void shouldReturnBidRequestWithSitePublisherIdFromAccountParamWhenSiteDoesNotExist() {
        // given
        routingContext.queryParams().add("account", "accountId");

        givenBidRequest(
                builder -> builder
                        .ext(ExtRequest.empty())
                        .site(null),
                Imp.builder().build());

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
    public void shouldReturnBidRequestWithSitePublisherIdFromAccountParamWhenSitePublisherDoesNotExist() {
        // given
        routingContext.queryParams().add("account", "accountId");

        givenBidRequest(
                builder -> builder
                        .ext(ExtRequest.empty())
                        .site(Site.builder().build()),
                Imp.builder().build());

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
                builder -> builder
                        .ext(ExtRequest.empty()),
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
                builder -> builder
                        .ext(ExtRequest.empty()),
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
    public void shouldReturnBidRequestWithOverriddenTmaxWhenTimeoutParamIsAvailable() {
        // given
        routingContext.queryParams().add("timeout", "1000");

        givenBidRequest(
                builder -> builder
                        .ext(ExtRequest.empty()),
                Imp.builder().build());

        // when
        final BidRequest request = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(request.getTmax()).isEqualTo(1000L);
    }

    @Test
    public void shouldReturnBidRequestWithUnmodifiedUserWhenGdprConsentParamIsBlank() {
        // given
        routingContext.queryParams().add("gdpr_consent", "");

        givenBidRequest(
                builder -> builder
                        .user(User.builder()
                                .ext(ExtUser.builder().consent("should-remain").build())
                                .build())
                        .ext(ExtRequest.empty()),
                Imp.builder().build());

        // when
        final BidRequest result = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(result.getUser()).isEqualTo(User.builder()
                .ext(ExtUser.builder().consent("should-remain").build())
                .build());
    }

    @Test
    public void shouldReturnBidRequestWithOverriddenUserExtConsentWhenGdprConsentParamIsValid() {
        // given
        routingContext.queryParams().add("gdpr_consent", "BONV8oqONXwgmADACHENAO7pqzAAppY");

        givenBidRequest(
                builder -> builder
                        .user(User.builder()
                                .id("1")
                                .ext(ExtUser.builder().consent("should-be-overridden").build())
                                .build())
                        .ext(ExtRequest.empty()),
                Imp.builder().build());

        // when
        final BidRequest result = target.fromRequest(routingContext, 0L).result().getBidRequest();

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
        routingContext.queryParams().add("gdpr_consent", "BONV8oqONXwgmADACHENAO7pqzAAppY");

        givenBidRequest(
                builder -> builder
                        .ext(ExtRequest.empty()),
                Imp.builder().build());

        // when
        final BidRequest result = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(result.getUser())
                .isEqualTo(User.builder()
                        .ext(ExtUser.builder().consent("BONV8oqONXwgmADACHENAO7pqzAAppY").build())
                        .build());
    }

    @Test
    public void shouldKeepEmptyUserWhenGdprConsentIsInvalid() {
        // given
        routingContext.queryParams().add("gdpr_consent", "consent-value");

        givenBidRequest(
                builder -> builder
                        .user(User.builder().build())
                        .ext(ExtRequest.empty()),
                Imp.builder().build());

        // when
        final BidRequest result = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(result.getUser())
                .isEqualTo(User.builder().build());
    }

    @Test
    public void shouldReturnAddErrorToAuctionContextWhenPrivacyIsNotValid() {
        // given
        routingContext.queryParams().add("gdpr_consent", "consent-value");

        givenBidRequest(
                builder -> builder
                        .user(User.builder().build())
                        .ext(ExtRequest.empty()),
                Imp.builder().build());

        // when
        target.fromRequest(routingContext, 0L).result();

        // then
        @SuppressWarnings("unchecked") final ArgumentCaptor<List<String>> errorsCaptor = ArgumentCaptor.forClass(
                List.class);
        verify(ortb2RequestFactory).fetchAccountAndCreateAuctionContext(any(), any(), any(), anyLong(), any(),
                errorsCaptor.capture());
        assertThat(errorsCaptor.getValue()).contains("Amp request parameter consent_string or gdpr_consent have"
                + " invalid format: consent-value");
    }

    @Test
    public void shouldReturnBidRequestWithExtPrebidDataBiddersUpdatedByFpdResolver() throws JsonProcessingException {
        // given
        routingContext.queryParams()
                .add("targeting", mapper.writeValueAsString(
                        Targeting.of(Arrays.asList("appnexus", "rubicon"), null, null)));

        given(fpdResolver.resolveBidRequestExt(any(), any()))
                .willReturn(ExtRequest.of(ExtRequestPrebid.builder()
                        .data(ExtRequestPrebidData.of(Arrays.asList("appnexus", "rubicon"), null)).build()));

        givenBidRequest(
                builder -> builder
                        .ext(ExtRequest.empty()),
                Imp.builder().build());

        // when
        final BidRequest request = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        verify(fpdResolver).resolveBidRequestExt(any(), any());
        assertThat(request)
                .extracting(BidRequest::getExt)
                .containsOnly(ExtRequest.of(ExtRequestPrebid.builder()
                        .data(ExtRequestPrebidData.of(Arrays.asList("appnexus", "rubicon"), null)).build()));
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

        givenBidRequest(
                builder -> builder
                        .ext(ExtRequest.empty()),
                Imp.builder().build());

        // when
        final BidRequest request = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        verify(fpdResolver).resolveBidRequestExt(any(), any());
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

        givenBidRequest(
                builder -> builder
                        .ext(ExtRequest.empty()),
                Imp.builder().build());

        // when
        final Future<AuctionContext> result = target.fromRequest(routingContext, 0L);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Error decoding targeting, expected type is `object` but was ARRAY");
    }

    @Test
    public void shouldReturnBidRequestWithoutRegsExtWhenNoPrivacyPolicyIsExist() {
        // given
        givenBidRequest(
                builder -> builder.ext(ExtRequest.empty()),
                Imp.builder().build());

        // when
        final BidRequest result = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(result.getRegs()).isNull();
    }

    @Test
    public void shouldReturnBidRequestWithRegsExtUsPrivacyWhenUsPrivacyParamIsValid() {
        // given
        routingContext.queryParams().add("gdpr_consent", "1N--");

        givenBidRequest(
                builder -> builder.ext(ExtRequest.empty()),
                Imp.builder().build());

        // when
        final BidRequest result = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(result.getRegs())
                .isEqualTo(Regs.of(null, ExtRegs.of(null, "1N--")));
    }

    @Test
    public void shouldReturnBidRequestWithRegsExtUsPrivacyWhenConsentStringIsValid() {
        // given
        routingContext.queryParams().add("consent_string", "1Y-N");

        givenBidRequest(
                builder -> builder
                        .user(User.builder().build())
                        .regs(Regs.of(1, ExtRegs.of(1, "replaced")))
                        .ext(ExtRequest.empty()),
                Imp.builder().build());

        // when
        final BidRequest result = target.fromRequest(routingContext, 0L).result().getBidRequest();

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
        final BidRequest request = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getDebug)
                .containsOnly(1);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldReturnBidRequestWithCreatedExtPrebidAmpData() {
        // given
        routingContext.queryParams()
                .add("queryParam1", "value1")
                .add("queryParam2", "value2");

        givenBidRequest(
                builder -> builder.ext(ExtRequest.of(null)),
                Imp.builder().build());

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

    @SuppressWarnings("unchecked")
    @Test
    public void shouldReturnBidRequestWithUpdatedExtPrebidAmpData() {
        // given
        routingContext.queryParams()
                .add("queryParam1", "value1")
                .add("queryParam2", "value2");

        final Map<String, String> existingAmpData = new HashMap<>();
        existingAmpData.put("queryParam2", "value2InRequest");
        existingAmpData.put("queryParam3", "value3");
        givenBidRequest(
                builder -> builder.ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .amp(ExtRequestPrebidAmp.of(existingAmpData))
                        .build())),
                Imp.builder().build());

        // when
        final BidRequest result = target.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        final Map<String, String> expectedAmpData = new HashMap<>();
        expectedAmpData.put("queryParam1", "value1");
        expectedAmpData.put("queryParam2", "value2");
        expectedAmpData.put("queryParam3", "value3");
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
        givenBidRequest(
                builder -> builder.ext(ExtRequest.empty()),
                Imp.builder().build());

        final BidRequest updatedBidRequest = defaultBidRequest.toBuilder().id("updated").build();
        given(ortb2ImplicitParametersResolver.resolve(any(), any(), any()))
                .willReturn(updatedBidRequest);

        // when
        final AuctionContext result = target.fromRequest(routingContext, 0L).result();

        // then
        assertThat(result.getBidRequest()).isEqualTo(updatedBidRequest);
    }

    @Test
    public void shouldReturnPopulatedPrivacyContextAndGetWhenPrivacyEnforcementReturnContext() {
        // given
        givenBidRequest(
                builder -> builder.ext(ExtRequest.empty()),
                Imp.builder().build());

        final GeoInfo geoInfo = GeoInfo.builder().vendor("vendor").city("found").build();
        final PrivacyContext privacyContext = PrivacyContext.of(
                Privacy.of("1", "consent", Ccpa.EMPTY, 0),
                TcfContext.builder().geoInfo(geoInfo).build());
        given(privacyEnforcementService.contextFromBidRequest(any()))
                .willReturn(Future.succeededFuture(privacyContext));

        // when
        final AuctionContext result = target.fromRequest(routingContext, 0L).result();

        // then
        assertThat(result.getPrivacyContext()).isEqualTo(privacyContext);
        assertThat(result.getGeoInfo()).isEqualTo(geoInfo);
    }

    @Test
    public void shouldPassHookExecutionContextWithAmpEndpoint() {
        // given
        givenBidRequest(
                builder -> builder.ext(ExtRequest.empty()),
                Imp.builder().build());

        // when
        target.fromRequest(routingContext, 0L);

        // then
        verify(ortb2RequestFactory).fetchAccountAndCreateAuctionContext(
                any(),
                any(),
                any(),
                anyLong(),
                argThat(context -> context.getEndpoint() == Endpoint.openrtb2_amp),
                any());
    }

    @Test
    public void shouldUseBidRequestModifiedByProcessedAuctionRequestHooks() {
        // given
        givenBidRequest(
                builder -> builder.site(Site.builder().domain("example.com").build()).ext(ExtRequest.empty()),
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
    public void shouldReturnFailedFutureIfProcessedAuctionRequestHookRejectRequest() {
        // given
        givenBidRequest(builder -> builder.ext(ExtRequest.empty()), Imp.builder().build());

        doAnswer(invocation -> Future.failedFuture(new RejectedRequestException(null)))
                .when(ortb2RequestFactory)
                .executeProcessedAuctionRequestHooks(any());

        // when
        final Future<?> future = target.fromRequest(routingContext, 0L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(RejectedRequestException.class);
    }

    private void givenBidRequest(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestBuilderCustomizer,
            Imp... imps) {
        final List<Imp> impList = imps.length > 0 ? asList(imps) : null;

        final BidRequest bidRequest = bidRequestBuilderCustomizer.apply(defaultBidRequest.toBuilder().imp(impList))
                .build();

        given(storedRequestProcessor.processAmpRequest(any(), anyString()))
                .willReturn(Future.succeededFuture(bidRequest));

        final MetricName metricName = MetricName.amp;
        given(ortb2RequestFactory.fetchAccountAndCreateAuctionContext(any(), any(), eq(metricName), anyLong(), any(),
                any()))
                .willAnswer(invocationOnMock -> Future.succeededFuture(
                        AuctionContext.builder()
                                .bidRequest((BidRequest) invocationOnMock.getArguments()[1])
                                .build()));

        given(ortb2ImplicitParametersResolver.resolve(any(), any(), any())).willAnswer(
                answerWithFirstArgument());
        given(ortb2RequestFactory.validateRequest(any())).willAnswer(answerWithFirstArgument());

        given(ortb2RequestFactory.enrichBidRequestWithAccountAndPrivacyData(any(), any(), any()))
                .willAnswer(answerWithFirstArgument());
        given(ortb2RequestFactory.executeProcessedAuctionRequestHooks(any()))
                .willAnswer(invocation -> Future.succeededFuture(
                        ((AuctionContext) invocation.getArgument(0)).getBidRequest()));

    }

    private Answer<Object> answerWithFirstArgument() {
        return invocationOnMock -> invocationOnMock.getArguments()[0];
    }

    private static Future<HttpRequestWrapper> toHttpRequest(RoutingContext routingContext, String body) {
        return Future.succeededFuture(HttpRequestWrapper.builder()
                .absoluteUri(routingContext.request().absoluteURI())
                .queryParams(routingContext.queryParams())
                .headers(routingContext.request().headers())
                .body(body)
                .scheme(routingContext.request().scheme())
                .remoteHost(routingContext.request().remoteAddress().host())
                .build());
    }

    private static ExtRequest givenRequestExt(ExtRequestTargeting extRequestTargeting) {
        return ExtRequest.of(ExtRequestPrebid.builder()
                .targeting(extRequestTargeting)
                .build());
    }
}
