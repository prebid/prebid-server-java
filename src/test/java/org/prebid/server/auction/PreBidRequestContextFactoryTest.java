package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Format;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.AdUnitBid;
import org.prebid.server.auction.model.AdapterRequest;
import org.prebid.server.auction.model.PreBidRequestContext;
import org.prebid.server.bidder.rubicon.proto.RubiconParams;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.proto.request.AdUnit;
import org.prebid.server.proto.request.Bid;
import org.prebid.server.proto.request.PreBidRequest;
import org.prebid.server.proto.request.PreBidRequest.PreBidRequestBuilder;
import org.prebid.server.proto.response.MediaType;
import org.prebid.server.settings.ApplicationSettings;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.EnumSet;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.apache.commons.lang3.math.NumberUtils.isDigits;
import static org.apache.commons.lang3.math.NumberUtils.toLong;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

public class PreBidRequestContextFactoryTest extends VertxTest {

    private static final Long HTTP_REQUEST_TIMEOUT = 250L;
    private static final String RUBICON = "rubicon";
    private static final String APPNEXUS = "appnexus";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private ImplicitParametersExtractor paramsExtractor;
    @Mock
    private ApplicationSettings applicationSettings;
    @Mock
    private UidsCookieService uidsCookieService;
    @Mock
    private UidsCookie uidsCookie;

    private PreBidRequestContextFactory factory;

    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpRequest;

    @Before
    public void setUp() {
        // minimal request
        given(routingContext.getBody()).willReturn(givenPreBidRequest(identity()));
        given(routingContext.request()).willReturn(httpRequest);

        given(paramsExtractor.refererFrom(any())).willReturn("http://referer");

        // parsed uids cookie
        given(uidsCookieService.parseFromRequest(any())).willReturn(uidsCookie);
        given(uidsCookie.hasLiveUids()).willReturn(false);

        final TimeoutFactory timeoutFactory = new TimeoutFactory(Clock.fixed(Instant.now(), ZoneId.systemDefault()));
        factory = new PreBidRequestContextFactory(HTTP_REQUEST_TIMEOUT, paramsExtractor, applicationSettings,
                uidsCookieService, timeoutFactory);
    }

    @Test
    public void shouldReturnPopulatedContext() {
        // given
        final AdUnit adUnit = AdUnit.builder()
                .code("adUnitCode1")
                .sizes(singletonList(Format.builder().w(100).h(100).build()))
                .bids(singletonList(givenBid(RUBICON)))
                .build();

        given(routingContext.getBody())
                .willReturn(givenPreBidRequest(builder -> builder.adUnits(singletonList(adUnit))));

        given(paramsExtractor.refererFrom(any())).willReturn("http://www.example.com");
        given(paramsExtractor.domainFrom(anyString())).willReturn("example.com");
        given(paramsExtractor.ipFrom(any())).willReturn("192.168.244.1");
        given(paramsExtractor.uaFrom(any())).willReturn("userAgent");
        given(paramsExtractor.secureFrom(any())).willReturn(1);

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.getAdapterRequests()).hasSize(1);
        assertThat(preBidRequestContext.getPreBidRequest()).isNotNull();
        assertThat(preBidRequestContext.getReferer()).isEqualTo("http://www.example.com");
        assertThat(preBidRequestContext.getDomain()).isEqualTo("example.com");
        assertThat(preBidRequestContext.getIp()).isEqualTo("192.168.244.1");
        assertThat(preBidRequestContext.getUa()).isEqualTo("userAgent");
        assertThat(preBidRequestContext.getSecure()).isEqualTo(1);
        assertThat(preBidRequestContext.isDebug()).isFalse();
        assertThat(preBidRequestContext.getUidsCookie()).isNotNull();
        assertThat(preBidRequestContext.isNoLiveUids()).isTrue();
    }

    @Test
    public void shouldFailIfRequestBodyCouldNotBeParsed() {
        // given
        given(routingContext.getBody()).willReturn(Buffer.buffer("{"));

        // when
        final Future<PreBidRequestContext> preBidRequestContextFuture = factory.fromRequest(routingContext);

        // then
        assertThat(preBidRequestContextFuture.failed()).isTrue();
        assertThat(preBidRequestContextFuture.cause())
                .isInstanceOf(PreBidException.class)
                .hasMessageStartingWith("Failed to decode")
                .hasCauseInstanceOf(JsonParseException.class);
    }

    @Test
    public void shouldFailIfRequestBodyIsMissing() {
        // given
        given(routingContext.getBody()).willReturn(null);

        // when
        final Future<PreBidRequestContext> preBidRequestContextFuture = factory.fromRequest(routingContext);

        // then
        assertThat(preBidRequestContextFuture.failed()).isTrue();
        assertThat(preBidRequestContextFuture.cause())
                .isInstanceOf(PreBidException.class).hasMessage("Incoming request has no body");
    }

    @Test
    public void shouldPopulateBidder() {
        // given
        final AdUnit adUnit = AdUnit.builder()
                .sizes(singletonList(Format.builder().w(300).h(250).build()))
                .topframe(1)
                .instl(1)
                .code("adUnitCode")
                .bids(singletonList(Bid.of("bidId", RUBICON, rubiconParams(1001, 2001, 3001))))
                .build();
        given(routingContext.getBody())
                .willReturn(givenPreBidRequest(builder -> builder.adUnits(singletonList(adUnit))));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.getAdapterRequests()).containsOnly(
                AdapterRequest.of(RUBICON, singletonList(AdUnitBid.builder()
                        .bidderCode(RUBICON)
                        .sizes(singletonList(Format.builder().w(300).h(250).build()))
                        .topframe(1)
                        .instl(1)
                        .adUnitCode("adUnitCode")
                        .bidId("bidId")
                        .params(rubiconParams(1001, 2001, 3001))
                        .mediaTypes(Collections.singleton(MediaType.banner))
                        .build())));
    }

    @Test
    public void shouldPopulateAdUnitBidWithBannerTypeIfMediaTypeIsNull() {
        // given
        final AdUnit adUnit = AdUnit.builder()
                .code("adUnitCode1")
                .sizes(singletonList(Format.builder().w(300).h(250).build()))
                .bids(singletonList(givenBid(RUBICON)))
                .build();

        given(routingContext.getBody())
                .willReturn(givenPreBidRequest(builder -> builder.adUnits(singletonList(adUnit))));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.getAdapterRequests().get(0).getAdUnitBids()).hasSize(1).element(0)
                .returns(Collections.singleton(MediaType.banner), AdUnitBid::getMediaTypes);
    }

    @Test
    public void shouldPopulateAdUnitBidWithBannerIfMediaTypeIsUnknown() {
        // given
        final AdUnit adUnit = AdUnit.builder()
                .code("adUnitCode1")
                .sizes(singletonList(Format.builder().w(300).h(250).build()))
                .bids(singletonList(givenBid(RUBICON)))
                .mediaTypes(singletonList("RandomMediaType"))
                .build();

        given(routingContext.getBody())
                .willReturn(givenPreBidRequest(builder -> builder.adUnits(singletonList(adUnit))));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.getAdapterRequests().get(0).getAdUnitBids()).hasSize(1).element(0)
                .returns(Collections.singleton(MediaType.banner), AdUnitBid::getMediaTypes);
    }

    @Test
    public void shouldPopulateAdUnitWithBannerAndVideoIfBothArePresent() {
        // given
        final AdUnit adUnit = AdUnit.builder()
                .code("adUnitCode1")
                .sizes(singletonList(Format.builder().w(300).h(250).build()))
                .bids(singletonList(givenBid(RUBICON)))
                .mediaTypes(asList("banner", "video"))
                .build();

        given(routingContext.getBody())
                .willReturn(givenPreBidRequest(builder -> builder.adUnits(singletonList(adUnit))));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.getAdapterRequests().get(0).getAdUnitBids()).hasSize(1).element(0)
                .returns(EnumSet.of(MediaType.banner, MediaType.video), AdUnitBid::getMediaTypes);
    }

    @Test
    public void shouldNotReturnAdUnitsWithNullSizes() {
        // given
        final AdUnit adUnit = AdUnit.builder()
                .bids(singletonList(givenBid(RUBICON)))
                .code("AdUnitCode1")
                .build();
        final AdUnit adUnit2 = AdUnit.builder()
                .bids(singletonList(givenBid(RUBICON)))
                .code("AdUnitCode2")
                .sizes(singletonList(Format.builder().w(300).h(250).build()))
                .build();

        given(routingContext.getBody())
                .willReturn(givenPreBidRequest(builder -> builder.adUnits(asList(adUnit, adUnit2))));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.getAdapterRequests().get(0).getAdUnitBids()).hasSize(1).element(0)
                .returns("AdUnitCode2", AdUnitBid::getAdUnitCode);
    }

    @Test
    public void shouldNotReturnAdUnitsWithNullAdUnitCode() {
        // given
        final AdUnit adUnit = AdUnit.builder()
                .bids(singletonList(givenBid(RUBICON)))
                .sizes(singletonList(Format.builder().w(300).h(250).build()))
                .build();
        final AdUnit adUnit2 = AdUnit.builder()
                .bids(singletonList(givenBid(RUBICON)))
                .code("AdUnitCode2")
                .sizes(singletonList(Format.builder().w(300).h(250).build()))
                .build();

        given(routingContext.getBody())
                .willReturn(givenPreBidRequest(builder -> builder.adUnits(asList(adUnit, adUnit2))));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.getAdapterRequests().get(0).getAdUnitBids()).hasSize(1).element(0)
                .returns("AdUnitCode2", AdUnitBid::getAdUnitCode);
    }

    @Test
    public void shouldExtractMultipleBidders() {
        // given
        final AdUnit adUnit = AdUnit.builder()
                .code("adUnitCode1")
                .sizes(singletonList(Format.builder().h(100).w(200).build()))
                .bids(asList(givenBid(RUBICON), givenBid(APPNEXUS)))
                .build();
        given(routingContext.getBody())
                .willReturn(givenPreBidRequest(builder -> builder.adUnits(asList(adUnit, adUnit))));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.getAdapterRequests()).hasSize(2)
                .flatExtracting(AdapterRequest::getAdUnitBids).hasSize(4);
    }

    @Test
    public void shouldExpandAdUnitConfig() throws JsonProcessingException {
        // given
        final AdUnit adUnit = AdUnit.builder()
                .configId("configId")
                .code("adUnitCode1")
                .sizes(singletonList(Format.builder().h(100).w(200).build()))
                .build();
        given(routingContext.getBody())
                .willReturn(givenPreBidRequest(builder -> builder.adUnits(singletonList(adUnit))));

        given(applicationSettings.getAdUnitConfigById(anyString(), any()))
                .willReturn(Future.succeededFuture(mapper.writeValueAsString(singletonList(
                        Bid.of("bidId", RUBICON, rubiconParams(4001, 5001, 6001))))));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        verify(applicationSettings).getAdUnitConfigById(anyString(), same(preBidRequestContext.getTimeout()));
        assertThat(preBidRequestContext.getAdapterRequests()).containsOnly(
                AdapterRequest.of(RUBICON, singletonList(AdUnitBid.builder()
                        .bidderCode(RUBICON)
                        .bidId("bidId")
                        .adUnitCode("adUnitCode1")
                        .sizes(singletonList(Format.builder().h(100).w(200).build()))
                        .params(rubiconParams(4001, 5001, 6001))
                        .mediaTypes(Collections.singleton(MediaType.banner))
                        .build())));
    }

    @Test
    public void shouldTolerateMissingAdUnitConfig() {
        // given
        final AdUnit adUnit = AdUnit.builder().configId("configId").build();
        given(routingContext.getBody())
                .willReturn(givenPreBidRequest(builder -> builder.adUnits(singletonList(adUnit))));

        given(applicationSettings.getAdUnitConfigById(anyString(), any()))
                .willReturn(Future.failedFuture(new PreBidException("Not found")));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.getAdapterRequests()).isEmpty();
    }

    @Test
    public void shouldTolerateInvalidAdUnitConfig() {
        // given
        final AdUnit adUnit = AdUnit.builder().configId("configId").build();
        given(routingContext.getBody())
                .willReturn(givenPreBidRequest(builder -> builder.adUnits(singletonList(adUnit))));

        given(applicationSettings.getAdUnitConfigById(anyString(), any()))
                .willReturn(Future.succeededFuture("invalid"));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.getAdapterRequests()).isEmpty();
    }

    @Test
    public void shouldGenerateBidIdIfAbsentInRequest() {
        // given
        given(routingContext.getBody()).willReturn(givenPreBidRequest(identity()));

        final AdUnit adUnit = AdUnit.builder()
                .code("adUnitCode1")
                .sizes(singletonList(Format.builder().w(100).h(100).build()))
                .bids(singletonList(givenBid(RUBICON)))
                .build();

        given(routingContext.getBody())
                .willReturn(givenPreBidRequest(builder -> builder.adUnits(singletonList(adUnit))));
        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.getAdapterRequests())
                .flatExtracting(AdapterRequest::getAdUnitBids)
                .extracting(AdUnitBid::getBidId)
                .element(0)
                .matches(id -> isDigits(id) && toLong(id) >= 0);
    }

    @Test
    public void shouldPickTimeoutFromRequest() {
        // given
        given(routingContext.getBody()).willReturn(givenPreBidRequest(builder -> builder.timeoutMillis(1000L)));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.getTimeout().remaining()).isEqualTo(1000L);
    }

    @Test
    public void shouldPickDefaultTimeoutIfZeroInRequest() {
        // given
        given(routingContext.getBody()).willReturn(givenPreBidRequest(builder -> builder.timeoutMillis(0L)));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.getTimeout().remaining()).isEqualTo(HTTP_REQUEST_TIMEOUT);
    }

    @Test
    public void shouldPickDefaultTimeoutIfGreaterThan2000InRequest() {
        // given
        given(routingContext.getBody()).willReturn(givenPreBidRequest(builder -> builder.timeoutMillis(5000L)));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.getTimeout().remaining()).isEqualTo(HTTP_REQUEST_TIMEOUT);
    }

    @Test
    public void shouldUpdateRequestTimeoutWithDefaultValueIfZeroInRequest() {
        // given
        given(routingContext.getBody()).willReturn(givenPreBidRequest(builder -> builder.timeoutMillis(0L)));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.getPreBidRequest().getTimeoutMillis()).isEqualTo(HTTP_REQUEST_TIMEOUT);
    }

    @Test
    public void shouldUpdateRequestTimeoutIfGreaterThan2000InRequest() {
        // given
        given(routingContext.getBody()).willReturn(givenPreBidRequest(builder -> builder.timeoutMillis(5000L)));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.getPreBidRequest().getTimeoutMillis()).isEqualTo(HTTP_REQUEST_TIMEOUT);
    }

    @Test
    public void shouldSetIsDebugToTrueIfTrueInPreBidRequest() {
        // given
        given(routingContext.getBody()).willReturn(givenPreBidRequest(builder -> builder.isDebug(true)));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.isDebug()).isTrue();
    }

    @Test
    public void shouldSetIsDebugToTrueIfQueryParameterEqualTo1() {
        // given
        given(routingContext.getBody()).willReturn(givenPreBidRequest(builder -> builder.isDebug(false)));
        given(httpRequest.getParam(eq("debug"))).willReturn("1");

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.isDebug()).isTrue();
    }

    @Test
    public void shouldSetIsDebugToFalseIfQueryParameterNotEqualTo1() {
        // given
        given(httpRequest.getParam(eq("debug"))).willReturn("2");

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.isDebug()).isFalse();
    }

    @Test
    public void shouldNotSetClientDataIfAppPresentInPreBidRequest() {
        // given
        given(routingContext.getBody()).willReturn(givenPreBidRequest(builder -> builder.app(App.builder().build())));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.getUidsCookie()).isNull();
        assertThat(preBidRequestContext.isNoLiveUids()).isFalse();
        assertThat(preBidRequestContext.getUa()).isNull();
        assertThat(preBidRequestContext.getReferer()).isNull();
        assertThat(preBidRequestContext.getDomain()).isNull();
    }

    @Test
    public void shouldFailIfRefererIsMissing() {
        // given
        final PreBidException exception = new PreBidException("Couldn't derive referer");
        given(paramsExtractor.refererFrom(any())).willThrow(exception);

        // when
        final Future<PreBidRequestContext> preBidRequestContextFuture = factory.fromRequest(routingContext);

        // then
        assertThat(preBidRequestContextFuture.failed()).isTrue();
        assertThat(preBidRequestContextFuture.cause()).isSameAs(exception);
    }

    @Test
    public void shouldFailIfDomainCouldNotBeExtracted() {
        // given
        final PreBidException exception = new PreBidException("Couldn't derive domain");
        given(paramsExtractor.domainFrom(any())).willThrow(exception);

        // when
        final Future<PreBidRequestContext> preBidRequestContextFuture = factory.fromRequest(routingContext);

        // then
        assertThat(preBidRequestContextFuture.failed()).isTrue();
        assertThat(preBidRequestContextFuture.cause()).isSameAs(exception);
    }

    private static Bid givenBid(String bidder) {
        return Bid.of(null, bidder, null);
    }

    private static Buffer givenPreBidRequest(Function<PreBidRequestBuilder, PreBidRequestBuilder> builderCustomizer) {
        try {
            return Buffer.buffer(mapper.writeValueAsString(
                    builderCustomizer.apply(
                            PreBidRequest.builder()
                                    .adUnits(singletonList(AdUnit.builder()
                                            .bids(singletonList(givenBid(RUBICON)))
                                            .build())))
                            .build()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException();
        }
    }

    private static ObjectNode rubiconParams(Integer accountId, Integer siteId, Integer zoneId) {
        return mapper.valueToTree(RubiconParams.builder()
                .accountId(accountId)
                .siteId(siteId)
                .zoneId(zoneId)
                .build());
    }
}
