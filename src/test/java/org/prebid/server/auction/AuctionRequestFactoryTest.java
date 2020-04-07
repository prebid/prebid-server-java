package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.cookie.model.UidWithExpiry;
import org.prebid.server.cookie.proto.Uids;
import org.prebid.server.exception.BlacklistedAccountException;
import org.prebid.server.exception.BlacklistedAppException;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.exception.UnauthorizedAccountException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.proto.openrtb.ext.request.ExtBidRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtGranularityRange;
import org.prebid.server.proto.openrtb.ext.request.ExtMediaTypePriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisher;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisherPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCacheBids;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCacheVastxml;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ExtUserDigiTrust;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.Account;
import org.prebid.server.validation.RequestValidator;
import org.prebid.server.validation.model.ValidationResult;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public class AuctionRequestFactoryTest extends VertxTest {

    private static final List<String> BLACKLISTED_APPS = singletonList("bad_app");
    private static final List<String> BLACKLISTED_ACCOUNTS = singletonList("bad_acc");

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private StoredRequestProcessor storedRequestProcessor;
    @Mock
    private ImplicitParametersExtractor paramsExtractor;
    @Mock
    private UidsCookieService uidsCookieService;
    @Mock
    private BidderCatalog bidderCatalog;
    @Mock
    private RequestValidator requestValidator;
    @Mock
    private InterstitialProcessor interstitialProcessor;
    @Mock
    private ApplicationSettings applicationSettings;

    private AuctionRequestFactory factory;
    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpRequest;
    @Mock
    private TimeoutResolver timeoutResolver;
    @Mock
    private TimeoutFactory timeoutFactory;

    @Before
    public void setUp() {
        given(interstitialProcessor.process(any())).will(invocationOnMock -> invocationOnMock.getArgument(0));

        given(routingContext.request()).willReturn(httpRequest);
        given(httpRequest.headers()).willReturn(new CaseInsensitiveHeaders());

        given(timeoutResolver.resolve(any())).willReturn(2000L);
        given(timeoutResolver.adjustTimeout(anyLong())).willReturn(1900L);

        factory = new AuctionRequestFactory(
                Integer.MAX_VALUE,
                false,
                false,
                "USD",
                BLACKLISTED_APPS,
                BLACKLISTED_ACCOUNTS,
                storedRequestProcessor,
                paramsExtractor,
                uidsCookieService,
                bidderCatalog,
                requestValidator,
                interstitialProcessor,
                timeoutResolver,
                timeoutFactory,
                applicationSettings,
                jacksonMapper);
    }

    @Test
    public void shouldReturnFailedFutureIfRequestBodyIsMissing() {
        // given
        given(routingContext.getBody()).willReturn(null);

        // when
        final Future<?> future = factory.fromRequest(routingContext, 0L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Incoming request has no body");
    }

    @Test
    public void shouldReturnFailedFutureIfAccountIsEnforcedAndIdIsNotProvided() {
        // given
        factory = new AuctionRequestFactory(
                1000,
                true,
                false,
                "USD",
                BLACKLISTED_APPS,
                BLACKLISTED_ACCOUNTS,
                storedRequestProcessor,
                paramsExtractor,
                uidsCookieService,
                bidderCatalog,
                requestValidator,
                interstitialProcessor,
                timeoutResolver,
                timeoutFactory,
                applicationSettings,
                jacksonMapper);

        givenValidBidRequest();

        // when
        final Future<?> future = factory.fromRequest(routingContext, 0L);

        // then
        verify(applicationSettings, never()).getAccountById(any(), any());

        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(UnauthorizedAccountException.class)
                .hasMessage("Unauthorised account id ");
    }

    @Test
    public void shouldReturnFailedFutureIfAccountIsEnforcedAndFailedGetAccountById() {
        // given
        factory = new AuctionRequestFactory(
                1000,
                true,
                false,
                "USD",
                BLACKLISTED_APPS,
                BLACKLISTED_ACCOUNTS,
                storedRequestProcessor,
                paramsExtractor,
                uidsCookieService,
                bidderCatalog,
                requestValidator,
                interstitialProcessor,
                timeoutResolver,
                timeoutFactory,
                applicationSettings,
                jacksonMapper);

        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.failedFuture(new PreBidException("Not found")));

        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder()
                        .publisher(Publisher.builder().id("absentId").build())
                        .build())
                .build();

        givenBidRequest(bidRequest);

        // when
        final Future<?> future = factory.fromRequest(routingContext, 0L);

        // then
        verify(applicationSettings).getAccountById(eq("absentId"), any());

        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(UnauthorizedAccountException.class)
                .hasMessage("Unauthorised account id absentId");
    }

    @Test
    public void shouldReturnFailedFutureIfRequestBodyExceedsMaxRequestSize() {
        // given
        factory = new AuctionRequestFactory(
                1,
                false,
                false,
                "USD",
                BLACKLISTED_APPS,
                BLACKLISTED_ACCOUNTS,
                storedRequestProcessor,
                paramsExtractor,
                uidsCookieService,
                bidderCatalog,
                requestValidator,
                interstitialProcessor,
                timeoutResolver,
                timeoutFactory,
                applicationSettings,
                jacksonMapper);

        given(routingContext.getBody()).willReturn(Buffer.buffer("body"));

        // when
        final Future<?> future = factory.fromRequest(routingContext, 0L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Request size exceeded max size of 1 bytes.");
    }

    @Test
    public void shouldReturnFailedFutureIfRequestBodyCouldNotBeParsed() {
        // given
        given(routingContext.getBody()).willReturn(Buffer.buffer("body"));

        // when
        final Future<?> future = factory.fromRequest(routingContext, 0L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(InvalidRequestException.class);
        assertThat(((InvalidRequestException) future.cause()).getMessages()).hasSize(1)
                .element(0).asString().startsWith("Error decoding bidRequest: Failed to decode:");
    }

    @Test
    public void shouldSetFieldsFromHeadersIfBodyFieldsEmpty() {
        // given
        givenValidBidRequest();

        givenImplicitParams("http://example.com", "example.com", "192.168.244.1", "UnitTest");

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(request.getSite()).isEqualTo(Site.builder()
                .page("http://example.com")
                .domain("example.com")
                .ext(mapper.valueToTree(ExtSite.of(0, null)))
                .build());
        assertThat(request.getDevice())
                .isEqualTo(Device.builder().ip("192.168.244.1").ua("UnitTest").build());
    }

    @Test
    public void shouldUpdateImpsWithSecurityOneIfRequestIsSecuredAndImpSecurityNotDefined() {
        // given
        givenBidRequest(BidRequest.builder().imp(singletonList(Imp.builder().build())).build());
        given(paramsExtractor.secureFrom(any())).willReturn(1);

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(request.getImp()).extracting(Imp::getSecure).containsOnly(1);
    }

    @Test
    public void shouldNotUpdateImpsWithSecurityOneIfRequestIsSecureAndImpSecurityIsZero() {
        // given
        givenBidRequest(BidRequest.builder().imp(singletonList(Imp.builder().secure(0).build())).build());
        given(paramsExtractor.secureFrom(any())).willReturn(1);

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(request.getImp()).extracting(Imp::getSecure).containsOnly(0);
    }

    @Test
    public void shouldUpdateImpsOnlyWithNotDefinedSecurityWithSecurityOneIfRequestIsSecure() {
        // given
        givenBidRequest(BidRequest.builder().imp(asList(Imp.builder().build(), Imp.builder().secure(0).build()))
                .build());
        given(paramsExtractor.secureFrom(any())).willReturn(1);

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(request.getImp()).extracting(Imp::getSecure).containsOnly(1, 0);
    }

    @Test
    public void shouldNotUpdateImpsWithSecurityOneIfRequestIsNotSecureAndImpSecurityIsNotDefined() {
        // given
        givenBidRequest(BidRequest.builder().imp(singletonList(Imp.builder().build())).build());
        given(paramsExtractor.secureFrom(any())).willReturn(0);

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(request.getImp()).extracting(Imp::getSecure).containsNull();
    }

    @Test
    public void shouldNotSetFieldsFromHeadersIfRequestFieldsNotEmpty() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().domain("test.com").page("http://test.com")
                        .ext(mapper.valueToTree(ExtSite.of(0, null))).build())
                .device(Device.builder().ua("UnitTestUA").ip("56.76.12.3").build())
                .user(User.builder().id("userId").build())
                .cur(singletonList("USD"))
                .tmax(2000L)
                .at(1)
                .build();

        givenBidRequest(bidRequest);

        givenImplicitParams("http://anotherexample.com", "anotherexample.com", "192.168.244.2", "UnitTest2");

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(request).isSameAs(bidRequest);
    }

    @Test
    public void shouldSetSiteExtIfNoReferer() {
        // given
        givenValidBidRequest();

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(request.getSite())
                .extracting(Site::getExt)
                .containsOnly(mapper.valueToTree(ExtSite.of(0, null)));
    }

    @Test
    public void shouldNotSetSitePageIfNoReferer() {
        // given
        givenBidRequest(BidRequest.builder()
                .site(Site.builder().domain("home.com").build())
                .build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(request.getSite()).isEqualTo(
                Site.builder().domain("home.com").ext(mapper.valueToTree(ExtSite.of(0, null))).build());
    }

    @Test
    public void shouldNotSetSitePageIfDomainCouldNotBeDerived() {
        // given
        givenBidRequest(BidRequest.builder()
                .site(Site.builder().domain("home.com").build())
                .build());

        given(paramsExtractor.refererFrom(any())).willReturn("http://not-valid-site");
        given(paramsExtractor.domainFrom(anyString())).willThrow(new PreBidException("Couldn't derive domain"));

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(request.getSite()).isEqualTo(
                Site.builder().domain("home.com").ext(mapper.valueToTree(ExtSite.of(0, null))).build());
    }

    @Test
    public void shouldSetSiteExtAmpIfSiteHasNoExt() {
        // given
        givenBidRequest(BidRequest.builder()
                .site(Site.builder().domain("test.com").page("http://test.com").build())
                .build());
        givenImplicitParams("http://anotherexample.com", "anotherexample.com", "192.168.244.2", "UnitTest2");

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(request.getSite()).isEqualTo(
                Site.builder().domain("test.com").page("http://test.com")
                        .ext(mapper.valueToTree(ExtSite.of(0, null))).build());
    }

    @Test
    public void shouldSetSiteExtAmpIfSiteExtHasNoAmp() {
        // given
        givenBidRequest(BidRequest.builder()
                .site(Site.builder().domain("test.com").page("http://test.com")
                        .ext(mapper.createObjectNode()).build())
                .build());
        givenImplicitParams("http://anotherexample.com", "anotherexample.com", "192.168.244.2", "UnitTest2");

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(request.getSite()).isEqualTo(
                Site.builder().domain("test.com").page("http://test.com")
                        .ext(mapper.valueToTree(ExtSite.of(0, null))).build());
    }

    @Test
    public void shouldSetSiteExtAmpIfNoReferer() {
        // given
        givenBidRequest(BidRequest.builder()
                .site(Site.builder().domain("test.com").page("http://test.com").build())
                .build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(request.getSite()).isEqualTo(
                Site.builder().domain("test.com").page("http://test.com")
                        .ext(mapper.valueToTree(ExtSite.of(0, null))).build());
    }

    @Test
    public void shouldSetUserExtDigitrustPerfIfNotDefined() {
        // given
        givenBidRequest(BidRequest.builder()
                .user(User.builder()
                        .ext(mapper.valueToTree(ExtUser.builder()
                                .digitrust(ExtUserDigiTrust.of("id", 123, null))
                                .build()))
                        .build())
                .build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(request.getUser().getExt())
                .isEqualTo(mapper.valueToTree(ExtUser.builder()
                        .digitrust(ExtUserDigiTrust.of("id", 123, 0))
                        .build()));
    }

    @Test
    public void shouldSetDefaultAtIfInitialValueIsEqualsToZero() {
        // given
        givenBidRequest(BidRequest.builder().at(0).build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(request.getAt()).isEqualTo(1);
    }

    @Test
    public void shouldSetDefaultAtIfInitialValueIsEqualsToNull() {
        // given
        givenBidRequest(BidRequest.builder().at(null).build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(request.getAt()).isEqualTo(1);
    }

    @Test
    public void shouldSetCurrencyIfMissedInRequestAndPresentInAdServerCurrencyConfig() {
        // given
        givenBidRequest(BidRequest.builder().cur(null).build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(request.getCur()).isEqualTo(singletonList("USD"));
    }

    @Test
    public void shouldSetTimeoutFromTimeoutResolver() {
        // given
        givenBidRequest(BidRequest.builder().build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(request.getTmax()).isEqualTo(2000L);
    }

    @Test
    public void shouldConvertStringPriceGranularityViewToCustom() {
        // given
        givenBidRequest(BidRequest.builder()
                .imp(singletonList(Imp.builder().ext(mapper.createObjectNode()).build()))
                .ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.builder().pricegranularity(new TextNode("low")).build())
                        .build())))
                .build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        // request was wrapped to list because extracting method works different on iterable and not iterable objects,
        // which force to make type casting or exception handling in lambdas
        assertThat(singletonList(request))
                .extracting(BidRequest::getExt)
                .extracting(ext -> mapper.treeToValue(ext, ExtBidRequest.class))
                .extracting(ExtBidRequest::getPrebid)
                .extracting(ExtRequestPrebid::getTargeting)
                .extracting(ExtRequestTargeting::getPricegranularity)
                .containsOnly(mapper.valueToTree(ExtPriceGranularity.of(2, singletonList(ExtGranularityRange.of(
                        BigDecimal.valueOf(5), BigDecimal.valueOf(0.5))))));
    }

    @Test
    public void shouldReturnFailedFutureWithInvalidRequestExceptionWhenStringPriceGranularityInvalid() {
        // given
        givenBidRequest(BidRequest.builder()
                .imp(singletonList(Imp.builder().build()))
                .ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.builder().pricegranularity(new TextNode("invalid")).build())
                        .build())))
                .build());

        // when
        final Future<?> future = factory.fromRequest(routingContext, 0L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Invalid string price granularity with value: invalid");
    }

    @Test
    public void shouldSetDefaultPriceGranularityIfPriceGranularityAndMediaTypePriceGranularityIsMissing() {
        // given
        givenBidRequest(BidRequest.builder()
                .imp(singletonList(Imp.builder().video(Video.builder().build()).ext(mapper.createObjectNode()).build()))
                .ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.builder().build())
                        .build())))
                .build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .extracting(BidRequest::getExt)
                .extracting(ext -> mapper.treeToValue(ext, ExtBidRequest.class))
                .extracting(ExtBidRequest::getPrebid)
                .extracting(ExtRequestPrebid::getTargeting)
                .extracting(ExtRequestTargeting::getPricegranularity)
                .containsOnly(mapper.valueToTree(ExtPriceGranularity.of(2, singletonList(ExtGranularityRange.of(
                        BigDecimal.valueOf(20), BigDecimal.valueOf(0.1))))));
    }

    @Test
    public void shouldNotSetDefaultPriceGranularityIfThereIsAMediaTypePriceGranularityForImpType() {
        // given
        final ExtMediaTypePriceGranularity mediaTypePriceGranularity = ExtMediaTypePriceGranularity.of(
                mapper.valueToTree(ExtPriceGranularity.of(2, singletonList(ExtGranularityRange.of(
                        BigDecimal.valueOf(20), BigDecimal.valueOf(0.1))))), null, null);
        givenBidRequest(BidRequest.builder()
                .imp(singletonList(Imp.builder().banner(Banner.builder().build())
                        .ext(mapper.createObjectNode()).build()))
                .ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.builder()
                                .mediatypepricegranularity(mediaTypePriceGranularity)
                                .build())
                        .build())))
                .build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .extracting(BidRequest::getExt)
                .extracting(ext -> mapper.treeToValue(ext, ExtBidRequest.class))
                .extracting(ExtBidRequest::getPrebid)
                .extracting(ExtRequestPrebid::getTargeting)
                .extracting(ExtRequestTargeting::getPricegranularity)
                .containsOnly(NullNode.getInstance());
    }

    @Test
    public void shouldSetDefaultIncludeWinnersIfIncludeWinnersIsMissed() {
        // given
        givenBidRequest(BidRequest.builder()
                .imp(singletonList(Imp.builder().ext(mapper.createObjectNode()).build()))
                .ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.builder().build())
                        .build())))
                .build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .extracting(BidRequest::getExt)
                .extracting(ext -> mapper.treeToValue(ext, ExtBidRequest.class))
                .extracting(ExtBidRequest::getPrebid)
                .extracting(ExtRequestPrebid::getTargeting)
                .extracting(ExtRequestTargeting::getIncludewinners)
                .containsOnly(true);
    }

    @Test
    public void shouldSetDefaultIncludeBidderKeysIfIncludeBidderKeysIsMissed() {
        // given
        givenBidRequest(BidRequest.builder()
                .imp(singletonList(Imp.builder().ext(mapper.createObjectNode()).build()))
                .ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.builder().build())
                        .build())))
                .build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .extracting(BidRequest::getExt)
                .extracting(ext -> mapper.treeToValue(ext, ExtBidRequest.class))
                .extracting(ExtBidRequest::getPrebid)
                .extracting(ExtRequestPrebid::getTargeting)
                .extracting(ExtRequestTargeting::getIncludebidderkeys)
                .containsOnly(true);
    }

    @Test
    public void shouldSetDefaultIncludeBidderKeysToFalseIfIncludeBidderKeysIsMissedAndWinningonlyIsTrue() {
        // given
        givenBidRequest(BidRequest.builder()
                .imp(singletonList(Imp.builder().ext(mapper.createObjectNode()).build()))
                .ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.builder().build())
                        .cache(ExtRequestPrebidCache.of(null, null, true))
                        .build())))
                .build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .extracting(BidRequest::getExt)
                .extracting(ext -> mapper.treeToValue(ext, ExtBidRequest.class))
                .extracting(ExtBidRequest::getPrebid)
                .extracting(ExtRequestPrebid::getTargeting)
                .extracting(ExtRequestTargeting::getIncludebidderkeys)
                .containsOnly(false);
    }

    @Test
    public void shouldSetDefaultIncludeBidderKeysToFalseIfIncludeBidderKeysIsMissedAndWinningonlyIsTrueInConfig() {
        // given
        factory = new AuctionRequestFactory(
                Integer.MAX_VALUE,
                false,
                true,
                "USD",
                BLACKLISTED_APPS,
                BLACKLISTED_ACCOUNTS,
                storedRequestProcessor,
                paramsExtractor,
                uidsCookieService,
                bidderCatalog,
                requestValidator,
                interstitialProcessor,
                timeoutResolver,
                timeoutFactory,
                applicationSettings,
                jacksonMapper);
        givenBidRequest(BidRequest.builder()
                .imp(singletonList(Imp.builder().ext(mapper.createObjectNode()).build()))
                .ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.builder().build())
                        .build())))
                .build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .extracting(BidRequest::getExt)
                .extracting(ext -> mapper.treeToValue(ext, ExtBidRequest.class))
                .extracting(ExtBidRequest::getPrebid)
                .extracting(ExtRequestPrebid::getTargeting)
                .extracting(ExtRequestTargeting::getIncludebidderkeys)
                .containsOnly(false);
    }

    @Test
    public void shouldSetCacheWinningonlyFromConfigWhenExtRequestPrebidIsNull() {
        // given
        factory = new AuctionRequestFactory(
                Integer.MAX_VALUE,
                false,
                true,
                "USD",
                BLACKLISTED_APPS,
                BLACKLISTED_ACCOUNTS,
                storedRequestProcessor,
                paramsExtractor,
                uidsCookieService,
                bidderCatalog,
                requestValidator,
                interstitialProcessor,
                timeoutResolver,
                timeoutFactory,
                applicationSettings,
                jacksonMapper);

        givenBidRequest(BidRequest.builder()
                .imp(singletonList(Imp.builder().ext(mapper.createObjectNode()).build()))
                .ext(mapper.valueToTree(ExtBidRequest.of(null)))
                .build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .extracting(BidRequest::getExt)
                .extracting(ext -> mapper.treeToValue(ext, ExtBidRequest.class))
                .extracting(ExtBidRequest::getPrebid)
                .extracting(ExtRequestPrebid::getCache)
                .extracting(ExtRequestPrebidCache::getWinningonly)
                .containsOnly(true);
    }

    @Test
    public void shouldSetCacheWinningonlyFromConfigWhenExtRequestPrebidCacheIsNull() {
        // given
        factory = new AuctionRequestFactory(
                Integer.MAX_VALUE,
                false,
                true,
                "USD",
                BLACKLISTED_APPS,
                BLACKLISTED_ACCOUNTS,
                storedRequestProcessor,
                paramsExtractor,
                uidsCookieService,
                bidderCatalog,
                requestValidator,
                interstitialProcessor,
                timeoutResolver,
                timeoutFactory,
                applicationSettings,
                jacksonMapper);

        givenBidRequest(BidRequest.builder()
                .imp(singletonList(Imp.builder().ext(mapper.createObjectNode()).build()))
                .ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder().build())))
                .build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .extracting(BidRequest::getExt)
                .extracting(ext -> mapper.treeToValue(ext, ExtBidRequest.class))
                .extracting(ExtBidRequest::getPrebid)
                .extracting(ExtRequestPrebid::getCache)
                .extracting(ExtRequestPrebidCache::getWinningonly)
                .containsOnly(true);
    }

    @Test
    public void shouldSetCacheWinningonlyFromConfigWhenCacheWinningonlyIsNull() {
        // given
        factory = new AuctionRequestFactory(
                Integer.MAX_VALUE,
                false,
                true,
                "USD",
                BLACKLISTED_APPS,
                BLACKLISTED_ACCOUNTS,
                storedRequestProcessor,
                paramsExtractor,
                uidsCookieService,
                bidderCatalog,
                requestValidator,
                interstitialProcessor,
                timeoutResolver,
                timeoutFactory,
                applicationSettings,
                jacksonMapper);

        givenBidRequest(BidRequest.builder()
                .imp(singletonList(Imp.builder().ext(mapper.createObjectNode()).build()))
                .ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .cache(ExtRequestPrebidCache.of(null, null, null))
                        .build())))
                .build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .extracting(BidRequest::getExt)
                .extracting(ext -> mapper.treeToValue(ext, ExtBidRequest.class))
                .extracting(ExtBidRequest::getPrebid)
                .extracting(ExtRequestPrebid::getCache)
                .extracting(ExtRequestPrebidCache::getWinningonly)
                .containsOnly(true);
    }

    @Test
    public void shouldNotChangeAnyOtherExtRequestPrebidCacheFields() {
        // given
        final ExtRequestPrebidCacheBids cacheBids = ExtRequestPrebidCacheBids.of(100, true);
        final ExtRequestPrebidCacheVastxml cacheVastxml = ExtRequestPrebidCacheVastxml.of(100, true);
        givenBidRequest(BidRequest.builder()
                .imp(singletonList(Imp.builder().ext(mapper.createObjectNode()).build()))
                .ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .cache(ExtRequestPrebidCache.of(cacheBids, cacheVastxml, null))
                        .build())))
                .build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .extracting(BidRequest::getExt)
                .extracting(ext -> mapper.treeToValue(ext, ExtBidRequest.class))
                .extracting(ExtBidRequest::getPrebid)
                .extracting(ExtRequestPrebid::getCache)
                .extracting(ExtRequestPrebidCache::getBids, ExtRequestPrebidCache::getVastxml)
                .containsOnly(tuple(cacheBids, cacheVastxml));
    }

    @Test
    public void shouldSetCacheWinningonlyFromRequestWhenCacheWinningonlyIsPresent() {
        // given
        factory = new AuctionRequestFactory(
                Integer.MAX_VALUE,
                false,
                true,
                "USD",
                BLACKLISTED_APPS,
                BLACKLISTED_ACCOUNTS,
                storedRequestProcessor,
                paramsExtractor,
                uidsCookieService,
                bidderCatalog,
                requestValidator,
                interstitialProcessor,
                timeoutResolver,
                timeoutFactory,
                applicationSettings,
                jacksonMapper);

        givenBidRequest(BidRequest.builder()
                .imp(singletonList(Imp.builder().ext(mapper.createObjectNode()).build()))
                .ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .cache(ExtRequestPrebidCache.of(null, null, false))
                        .build())))
                .build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .extracting(BidRequest::getExt)
                .extracting(ext -> mapper.treeToValue(ext, ExtBidRequest.class))
                .extracting(ExtBidRequest::getPrebid)
                .extracting(ExtRequestPrebid::getCache)
                .extracting(ExtRequestPrebidCache::getWinningonly)
                .containsOnly(false);
    }

    @Test
    public void shouldNotSetCacheWinningonlyFromConfigWhenCacheWinningonlyIsNullAndConfigValueIsFalse() {
        // given
        factory = new AuctionRequestFactory(
                Integer.MAX_VALUE,
                false,
                false,
                "USD",
                BLACKLISTED_APPS,
                BLACKLISTED_ACCOUNTS,
                storedRequestProcessor,
                paramsExtractor,
                uidsCookieService,
                bidderCatalog,
                requestValidator,
                interstitialProcessor,
                timeoutResolver,
                timeoutFactory,
                applicationSettings,
                jacksonMapper);

        final ObjectNode extBidRequest = mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                .cache(ExtRequestPrebidCache.of(null, null, null))
                .build()));

        givenBidRequest(BidRequest.builder()
                .imp(singletonList(Imp.builder().ext(mapper.createObjectNode()).build()))
                .ext(extBidRequest)
                .build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(request.getExt()).isSameAs(extBidRequest);
    }

    @Test
    public void shouldAddMissingAliases() {
        // given
        final Imp imp1 = Imp.builder()
                .ext(mapper.createObjectNode()
                        .set("requestScopedBidderAlias", mapper.createObjectNode()))
                .build();
        final Imp imp2 = Imp.builder()
                .ext(mapper.createObjectNode()
                        .set("configScopedBidderAlias", mapper.createObjectNode()))
                .build();

        givenBidRequest(BidRequest.builder()
                .imp(asList(imp1, imp2))
                .ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .aliases(singletonMap("requestScopedBidderAlias", "bidder1"))
                        .targeting(ExtRequestTargeting.builder().build())
                        .build())))
                .build());

        given(bidderCatalog.isAlias("configScopedBidderAlias")).willReturn(true);
        given(bidderCatalog.nameByAlias("configScopedBidderAlias")).willReturn("bidder2");

        // when
        final Future<AuctionContext> auctionContextFuture = factory.fromRequest(routingContext, 0L);
        final BidRequest request = auctionContextFuture.result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .extracting(BidRequest::getExt)
                .extracting(ext -> mapper.treeToValue(ext, ExtBidRequest.class))
                .extracting(ExtBidRequest::getPrebid)
                .flatExtracting(extRequestPrebid -> extRequestPrebid.getAliases().entrySet())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple("requestScopedBidderAlias", "bidder1"),
                        tuple("configScopedBidderAlias", "bidder2"));
    }

    @Test
    public void shouldTolerateMissingImpExtWhenProcessingAliases() {
        // given
        givenBidRequest(BidRequest.builder()
                .imp(singletonList(Imp.builder().ext(null).build()))
                .ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .aliases(singletonMap("alias", "bidder"))
                        .build())))
                .build());

        // when
        final Future<AuctionContext> future = factory.fromRequest(routingContext, 0L);

        // then
        assertThat(future.succeeded()).isTrue();
    }

    @Test
    public void shouldPassExtPrebidDebugFlagIfPresent() {
        // given
        givenBidRequest(BidRequest.builder()
                .imp(singletonList(Imp.builder().ext(mapper.createObjectNode()).build()))
                .ext(mapper.valueToTree(ExtBidRequest.of(ExtRequestPrebid.builder()
                        .debug(1)
                        .build())))
                .build());

        // when
        final BidRequest request = factory.fromRequest(routingContext, 0L).result().getBidRequest();

        // then
        assertThat(singletonList(request))
                .extracting(BidRequest::getExt)
                .extracting(ext -> mapper.treeToValue(ext, ExtBidRequest.class))
                .extracting(ExtBidRequest::getPrebid)
                .extracting(ExtRequestPrebid::getDebug)
                .containsOnly(1);
    }

    @Test
    public void shouldReturnFailedFutureIfRequestValidationFailed() {
        // given
        given(routingContext.getBody()).willReturn(Buffer.buffer("{}"));

        given(storedRequestProcessor.processStoredRequests(any())).willReturn(Future.succeededFuture(
                BidRequest.builder().build()));

        given(requestValidator.validate(any())).willReturn(new ValidationResult(asList("error1", "error2")));

        // when
        final Future<?> future = factory.fromRequest(routingContext, 0L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(InvalidRequestException.class);
        assertThat(((InvalidRequestException) future.cause()).getMessages()).containsOnly("error1", "error2");
    }

    @Test
    public void shouldReturnAuctionContextWithRoutingContext() {
        // given
        givenValidBidRequest();

        // when
        final RoutingContext context = factory.fromRequest(routingContext, 0L).result().getRoutingContext();

        // then
        assertThat(context).isSameAs(routingContext);
    }

    @Test
    public void shouldReturnAuctionContextWithUidsCookie() {
        // given
        givenValidBidRequest();

        final UidsCookie givenUidsCookie = new UidsCookie(Uids.builder()
                .uids(singletonMap("bidder", UidWithExpiry.live("uid")))
                .build(), jacksonMapper);
        given(uidsCookieService.parseFromRequest(any())).willReturn(givenUidsCookie);

        // when
        final UidsCookie uidsCookie = factory.fromRequest(routingContext, 0L).result().getUidsCookie();

        // then
        assertThat(uidsCookie).isSameAs(givenUidsCookie);
    }

    @Test
    public void shouldReturnAuctionContextWithTimeout() {
        // given
        givenValidBidRequest();

        given(timeoutFactory.create(anyLong(), anyLong())).willReturn(mock(Timeout.class));

        final long startTime = Clock.fixed(Instant.now(), ZoneId.systemDefault()).millis();

        // when
        final Timeout timeout = factory.fromRequest(routingContext, startTime).result().getTimeout();

        // then
        verify(timeoutFactory).create(eq(startTime), anyLong());
        assertThat(timeout).isNotNull();
    }

    @Test
    public void shouldReturnFailedFutureWhenAccountIdIsBlacklisted() {
        // given
        givenBidRequest(BidRequest.builder()
                .site(Site.builder()
                        .publisher(Publisher.builder().id("bad_acc").build()).build())
                .build());

        // when
        final Future<AuctionContext> result = factory.fromRequest(routingContext, 0);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
                .isInstanceOf(BlacklistedAccountException.class)
                .hasMessage("Prebid-server has blacklisted Account ID: bad_acc, please reach out to the prebid "
                        + "server host.");
    }

    @Test
    public void shouldReturnFailedFutureWhenAppIdIsBlacklisted() {
        // given
        givenBidRequest(BidRequest.builder()
                .app(App.builder().id("bad_app").build())
                .build());

        // when
        final Future<AuctionContext> result = factory.fromRequest(routingContext, 0);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
                .isInstanceOf(BlacklistedAppException.class)
                .hasMessage("Prebid-server does not process requests from App ID: bad_app");
    }

    @Test
    public void shouldReturnAuctionContextWithAccountIdTakenFromPublisherExt() {
        // given
        givenBidRequest(BidRequest.builder()
                .site(Site.builder()
                        .publisher(Publisher.builder().id("accountId")
                                .ext(mapper.valueToTree(ExtPublisher.of(ExtPublisherPrebid.of("parentAccount"))))
                                .build())
                        .build())
                .build());

        final Account givenAccount = Account.builder().id("parentAccount").build();
        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.succeededFuture(givenAccount));

        // when
        final Account account = factory.fromRequest(routingContext, 0L).result().getAccount();

        // then
        verify(applicationSettings).getAccountById(eq("parentAccount"), any());

        assertThat(account).isSameAs(givenAccount);
    }

    @Test
    public void shouldReturnAuctionContextWithAccountIdTakenFromPublisherIdWhenExtIsNull() {
        // given
        givenBidRequest(BidRequest.builder()
                .site(Site.builder()
                        .publisher(Publisher.builder().id("accountId").ext(null).build())
                        .build())
                .build());

        final Account givenAccount = Account.builder().id("accountId").build();
        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.succeededFuture(givenAccount));

        // when
        final Account account = factory.fromRequest(routingContext, 0L).result().getAccount();

        // then
        verify(applicationSettings).getAccountById(eq("accountId"), any());

        assertThat(account).isSameAs(givenAccount);
    }

    @Test
    public void shouldReturnAuctionContextWithAccountIdTakenFromPublisherIdWhenExtPublisherPrebidIsNull() {
        // given
        givenBidRequest(BidRequest.builder()
                .site(Site.builder()
                        .publisher(Publisher.builder().id("accountId")
                                .ext(mapper.valueToTree(ExtPublisher.of(null))).build())
                        .build())
                .build());

        final Account givenAccount = Account.builder().id("accountId").build();
        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.succeededFuture(givenAccount));

        // when
        final Account account = factory.fromRequest(routingContext, 0L).result().getAccount();

        // then
        verify(applicationSettings).getAccountById(eq("accountId"), any());

        assertThat(account).isSameAs(givenAccount);
    }

    @Test
    public void shouldReturnAuctionContextWithAccountIdTakenFromPublisherIdWhenExtParentIsEmpty() {
        // given
        givenBidRequest(BidRequest.builder()
                .site(Site.builder()
                        .publisher(Publisher.builder().id("accountId")
                                .ext(mapper.valueToTree(ExtPublisher.of(ExtPublisherPrebid.of("")))).build())
                        .build())
                .build());

        final Account givenAccount = Account.builder().id("accountId").build();
        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.succeededFuture(givenAccount));

        // when
        final Account account = factory.fromRequest(routingContext, 0L).result().getAccount();

        // then
        verify(applicationSettings).getAccountById(eq("accountId"), any());

        assertThat(account).isSameAs(givenAccount);
    }

    @Test
    public void shouldReturnAuctionContextWithEmptyAccountIfNotFound() {
        // given
        givenBidRequest(BidRequest.builder()
                .site(Site.builder()
                        .publisher(Publisher.builder().id("accountId")
                                .ext(mapper.valueToTree(ExtPublisher.of(ExtPublisherPrebid.of("parentAccount"))))
                                .build())
                        .build())
                .build());

        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.failedFuture(new PreBidException("not found")));

        // when
        final Account account = factory.fromRequest(routingContext, 0L).result().getAccount();

        // then
        verify(applicationSettings).getAccountById(eq("parentAccount"), any());

        assertThat(account).isEqualTo(Account.builder().id("parentAccount").build());
    }

    @Test
    public void shouldReturnAuctionContextWithEmptyAccountIfExceptionOccurred() {
        // given
        givenBidRequest(BidRequest.builder()
                .site(Site.builder()
                        .publisher(Publisher.builder().id("accountId").build())
                        .build())
                .build());

        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.failedFuture(new RuntimeException("error")));

        // when
        final Account account = factory.fromRequest(routingContext, 0L).result().getAccount();

        // then
        verify(applicationSettings).getAccountById(eq("accountId"), any());

        assertThat(account).isEqualTo(Account.builder().id("accountId").build());
    }

    @Test
    public void shouldReturnAuctionContextWithEmptyAccountIfItIsMissingInRequest() {
        // given
        givenValidBidRequest();

        // when
        final Account account = factory.fromRequest(routingContext, 0L).result().getAccount();

        // then
        assertThat(account).isEqualTo(Account.builder().id("").build());
        verifyZeroInteractions(applicationSettings);
    }

    private void givenImplicitParams(String referer, String domain, String ip, String ua) {
        given(paramsExtractor.refererFrom(any())).willReturn(referer);
        given(paramsExtractor.domainFrom(anyString())).willReturn(domain);
        given(paramsExtractor.ipFrom(any())).willReturn(ip);
        given(paramsExtractor.uaFrom(any())).willReturn(ua);
    }

    private void givenBidRequest(BidRequest bidRequest) {
        try {
            given(routingContext.getBody()).willReturn(Buffer.buffer(mapper.writeValueAsString(bidRequest)));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        given(storedRequestProcessor.processStoredRequests(any())).willReturn(Future.succeededFuture(bidRequest));

        given(requestValidator.validate(any())).willReturn(ValidationResult.success());
    }

    private void givenValidBidRequest() {
        givenBidRequest(BidRequest.builder().build());
    }
}
