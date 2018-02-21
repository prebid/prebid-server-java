package org.rtb.vexing.auction;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import de.malkusch.whoisServerList.publicSuffixList.PublicSuffixList;
import de.malkusch.whoisServerList.publicSuffixList.PublicSuffixListFactory;
import io.vertx.core.Future;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.impl.SocketAddressImpl;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.rtb.vexing.VertxTest;
import org.rtb.vexing.adapter.rubicon.model.RubiconParams;
import org.rtb.vexing.cookie.UidsCookie;
import org.rtb.vexing.cookie.UidsCookieService;
import org.rtb.vexing.exception.PreBidException;
import org.rtb.vexing.model.AdUnitBid;
import org.rtb.vexing.model.Bidder;
import org.rtb.vexing.model.MediaType;
import org.rtb.vexing.model.PreBidRequestContext;
import org.rtb.vexing.model.request.AdUnit;
import org.rtb.vexing.model.request.Bid;
import org.rtb.vexing.model.request.PreBidRequest;
import org.rtb.vexing.model.request.PreBidRequest.PreBidRequestBuilder;
import org.rtb.vexing.settings.ApplicationSettings;

import java.net.MalformedURLException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.function.Function;

import static io.vertx.core.http.HttpHeaders.REFERER;
import static io.vertx.core.http.HttpHeaders.USER_AGENT;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.apache.commons.lang3.math.NumberUtils.isDigits;
import static org.apache.commons.lang3.math.NumberUtils.toLong;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

public class PreBidRequestContextFactoryTest extends VertxTest {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final Long HTTP_REQUEST_TIMEOUT = 250L;
    private static final String RUBICON = "rubicon";
    private static final String APPNEXUS = "appnexus";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();
    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpRequest;
    private PublicSuffixList psl = new PublicSuffixListFactory().build();
    @Mock
    private ApplicationSettings applicationSettings;
    @Mock
    private UidsCookieService uidsCookieService;
    @Mock
    private UidsCookie uidsCookie;

    private PreBidRequestContextFactory factory;

    @Before
    public void setUp() {
        // minimal request
        given(routingContext.getBodyAsJson()).willReturn(givenPreBidRequest(identity()));
        given(routingContext.request()).willReturn(httpRequest);
        given(httpRequest.headers()).willReturn(new CaseInsensitiveHeaders());
        given(httpRequest.remoteAddress()).willReturn(new SocketAddressImpl(0, "192.168.244.1"));
        httpRequest.headers().set(REFERER, "http://example.com");

        // parsed uids cookie
        given(uidsCookieService.parseFromRequest(any())).willReturn(uidsCookie);
        given(uidsCookie.hasLiveUids()).willReturn(false);

        factory = new PreBidRequestContextFactory(HTTP_REQUEST_TIMEOUT, psl, applicationSettings, uidsCookieService);
    }

    @Test
    public void createShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new PreBidRequestContextFactory(null, null, null, null));
        assertThatNullPointerException().isThrownBy(() -> new PreBidRequestContextFactory(1L, psl, applicationSettings,
                null));
        assertThatNullPointerException().isThrownBy(() -> new PreBidRequestContextFactory(1L, psl, null,
                uidsCookieService));
        assertThatNullPointerException().isThrownBy(() -> new PreBidRequestContextFactory(1L, null, applicationSettings,
                uidsCookieService));
        assertThatNullPointerException().isThrownBy(() -> new PreBidRequestContextFactory(null, psl,
                applicationSettings, uidsCookieService));

    }

    @Test
    public void shouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> factory.fromRequest(null));
    }

    @Test
    public void shouldReturnPopulatedContext() {
        // given
        httpRequest.headers().set(USER_AGENT, "userAgent");
        httpRequest.headers().set(REFERER, "http://www.example.com");

        final AdUnit adUnit = AdUnit.builder()
                .code("adUnitCode1")
                .sizes(Collections.singletonList(Format.builder().w(100).h(100).build()))
                .bids(singletonList(givenBid(RUBICON)))
                .build();

        given(routingContext.getBodyAsJson()).willReturn(
                givenPreBidRequest(builder -> builder.adUnits(Collections.singletonList(adUnit))));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.getBidders()).hasSize(1);
        assertThat(preBidRequestContext.getPreBidRequest()).isNotNull();
        assertThat(preBidRequestContext.getIp()).isEqualTo("192.168.244.1");
        assertThat(preBidRequestContext.getSecure()).isNull();
        assertThat(preBidRequestContext.isDebug()).isFalse();
        assertThat(preBidRequestContext.getUidsCookie()).isNotNull();
        assertThat(preBidRequestContext.isNoLiveUids()).isTrue();
        assertThat(preBidRequestContext.getUa()).isEqualTo("userAgent");
        assertThat(preBidRequestContext.getReferer()).isEqualTo("http://www.example.com");
        assertThat(preBidRequestContext.getDomain()).isEqualTo("example.com");
    }

    @Test
    public void shouldFailIfRequestBodyCouldNotBeParsed() {
        // given
        given(routingContext.getBodyAsJson()).willThrow(
                new DecodeException("Could not parse", new JsonParseException(null, (String) null)));

        // when
        final Future<PreBidRequestContext> preBidRequestContextFuture = factory.fromRequest(routingContext);

        // then
        assertThat(preBidRequestContextFuture.failed()).isTrue();
        assertThat(preBidRequestContextFuture.cause())
                .isInstanceOf(PreBidException.class)
                .hasMessage("Could not parse")
                .hasCauseInstanceOf(JsonParseException.class);
    }

    @Test
    public void shouldFailIfRequestBodyIsMissing() {
        // given
        given(routingContext.getBodyAsJson()).willReturn(null);

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
        given(routingContext.getBodyAsJson()).willReturn(
                givenPreBidRequest(builder -> builder.adUnits(singletonList(adUnit))));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.getBidders()).containsOnly(Bidder.of(RUBICON, singletonList(AdUnitBid.builder()
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

        given(routingContext.getBodyAsJson()).willReturn(
                givenPreBidRequest(builder -> builder.adUnits(singletonList(adUnit))));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.getBidders().get(0).getAdUnitBids()).hasSize(1).element(0)
                .returns(Collections.singleton(MediaType.banner), AdUnitBid::getMediaTypes);
    }

    @Test
    public void shouldPopulateAdUnitBidWithBannerIfMediaTypeIsUnknown() {
        // given
        final AdUnit adUnit = AdUnit.builder()
                .code("adUnitCode1")
                .sizes(singletonList(Format.builder().w(300).h(250).build()))
                .bids(singletonList(givenBid(RUBICON)))
                .mediaTypes(Collections.singletonList("RandomMediaType"))
                .build();

        given(routingContext.getBodyAsJson()).willReturn(
                givenPreBidRequest(builder -> builder.adUnits(singletonList(adUnit))));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.getBidders().get(0).getAdUnitBids()).hasSize(1).element(0)
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

        given(routingContext.getBodyAsJson()).willReturn(
                givenPreBidRequest(builder -> builder.adUnits(singletonList(adUnit))));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.getBidders().get(0).getAdUnitBids()).hasSize(1).element(0)
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

        given(routingContext.getBodyAsJson()).willReturn(
                givenPreBidRequest(builder -> builder.adUnits(asList(adUnit, adUnit2))));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.getBidders().get(0).getAdUnitBids()).hasSize(1).element(0)
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

        given(routingContext.getBodyAsJson()).willReturn(
                givenPreBidRequest(builder -> builder.adUnits(asList(adUnit, adUnit2))));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.getBidders().get(0).getAdUnitBids()).hasSize(1).element(0)
                .returns("AdUnitCode2", AdUnitBid::getAdUnitCode);
    }

    @Test
    public void shouldExtractMultipleBidders() {
        // given
        final AdUnit adUnit = AdUnit.builder()
                .code("adUnitCode1")
                .sizes(Collections.singletonList(Format.builder().h(100).w(200).build()))
                .bids(asList(givenBid(RUBICON), givenBid(APPNEXUS)))
                .build();
        given(routingContext.getBodyAsJson()).willReturn(
                givenPreBidRequest(builder -> builder.adUnits(asList(adUnit, adUnit))));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.getBidders()).hasSize(2)
                .flatExtracting(Bidder::getAdUnitBids).hasSize(4);
    }

    @Test
    public void shouldExpandAdUnitConfig() throws JsonProcessingException {
        // given
        final AdUnit adUnit = AdUnit.builder()
                .configId("configId")
                .code("adUnitCode1")
                .sizes(Collections.singletonList(Format.builder().h(100).w(200).build()))
                .build();
        given(routingContext.getBodyAsJson()).willReturn(
                givenPreBidRequest(builder -> builder.adUnits(singletonList(adUnit))));

        given(applicationSettings.getAdUnitConfigById(anyString(), any()))
                .willReturn(Future.succeededFuture(mapper.writeValueAsString(singletonList(
                        Bid.of("bidId", RUBICON, rubiconParams(4001, 5001, 6001))))));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        verify(applicationSettings).getAdUnitConfigById(anyString(), same(preBidRequestContext.getTimeout()));
        assertThat(preBidRequestContext.getBidders()).containsOnly(Bidder.of(RUBICON, singletonList(AdUnitBid.builder()
                .bidderCode(RUBICON)
                .bidId("bidId")
                .adUnitCode("adUnitCode1")
                .sizes(Collections.singletonList(Format.builder().h(100).w(200).build()))
                .params(rubiconParams(4001, 5001, 6001))
                .mediaTypes(Collections.singleton(MediaType.banner))
                .build())));
    }

    @Test
    public void shouldTolerateMissingAdUnitConfig() {
        // given
        final AdUnit adUnit = AdUnit.builder().configId("configId").build();
        given(routingContext.getBodyAsJson()).willReturn(
                givenPreBidRequest(builder -> builder.adUnits(singletonList(adUnit))));

        given(applicationSettings.getAdUnitConfigById(anyString(), any()))
                .willReturn(Future.failedFuture(new PreBidException("Not found")));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.getBidders()).isEmpty();
    }

    @Test
    public void shouldTolerateInvalidAdUnitConfig() {
        // given
        final AdUnit adUnit = AdUnit.builder().configId("configId").build();
        given(routingContext.getBodyAsJson()).willReturn(
                givenPreBidRequest(builder -> builder.adUnits(singletonList(adUnit))));

        given(applicationSettings.getAdUnitConfigById(anyString(), any()))
                .willReturn(Future.succeededFuture("invalid"));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.getBidders()).isEmpty();
    }

    @Test
    public void shouldGenerateBidIdIfAbsentInRequest() {
        // given
        given(routingContext.getBodyAsJson()).willReturn(givenPreBidRequest(identity()));

        final AdUnit adUnit = AdUnit.builder()
                .code("adUnitCode1")
                .sizes(Collections.singletonList(Format.builder().w(100).h(100).build()))
                .bids(singletonList(givenBid(RUBICON)))
                .build();

        given(routingContext.getBodyAsJson()).willReturn(
                givenPreBidRequest(builder -> builder.adUnits(Collections.singletonList(adUnit))));
        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.getBidders())
                .flatExtracting(Bidder::getAdUnitBids)
                .extracting(AdUnitBid::getBidId)
                .element(0)
                .matches(id -> isDigits(id) && toLong(id) >= 0);
    }

    @Test
    public void shouldPickTimeoutFromRequest() {
        // given
        given(routingContext.getBodyAsJson()).willReturn(
                givenPreBidRequest(builder -> builder.timeoutMillis(1000L)));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.getTimeout().remaining()).isCloseTo(1000L, offset(20L));
    }

    @Test
    public void shouldPickDefaultTimeoutIfZeroInRequest() {
        // given
        given(routingContext.getBodyAsJson()).willReturn(
                givenPreBidRequest(builder -> builder.timeoutMillis(0L)));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.getTimeout().remaining()).isCloseTo(HTTP_REQUEST_TIMEOUT, offset(20L));
    }

    @Test
    public void shouldPickDefaultTimeoutIfGreaterThan2000InRequest() {
        // given
        given(routingContext.getBodyAsJson()).willReturn(
                givenPreBidRequest(builder -> builder.timeoutMillis(5000L)));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.getTimeout().remaining()).isCloseTo(HTTP_REQUEST_TIMEOUT, offset(20L));
    }

    @Test
    public void shouldSetSecureFlagIfXForwardedProtoIsHttps() {
        // given
        httpRequest.headers().set("X-Forwarded-Proto", "https");

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.getSecure()).isEqualTo(1);
    }


    @Test
    public void shouldSetSecureFlagIfConnectedViaSSL() {
        // given
        given(httpRequest.scheme()).willReturn("https");

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.getSecure()).isEqualTo(1);
    }

    @Test
    public void shouldNotSetSecureFlag() {
        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.getSecure()).isNull();
    }

    @Test
    public void shouldSetSingleIpFromXForwardedFor() {
        // given
        httpRequest.headers().set(X_FORWARDED_FOR, " 192.168.144.1 ");

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.getIp()).isEqualTo("192.168.144.1");
    }

    @Test
    public void shouldSetFirstIpFromXForwardedFor() {
        // given
        httpRequest.headers().set(X_FORWARDED_FOR, " 192.168.44.1 , 192.168.144.1 , 192.168.244.1 ");

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.getIp()).isEqualTo("192.168.44.1");
    }

    @Test
    public void shouldSetIpFromXRealIP() {
        // given
        httpRequest.headers().set("X-Real-IP", " 192.168.44.1 ");

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.getIp()).isEqualTo("192.168.44.1");
    }

    @Test
    public void shouldSetIsDebugToTrueIfTrueInPreBidRequest() {
        // given
        given(routingContext.getBodyAsJson()).willReturn(
                givenPreBidRequest(builder -> builder.isDebug(true)));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.isDebug()).isTrue();
    }

    @Test
    public void shouldSetIsDebugToTrueIfQueryParameterEqualTo1() {
        // given
        given(routingContext.getBodyAsJson()).willReturn(
                givenPreBidRequest(builder -> builder.isDebug(false)));
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
        given(routingContext.getBodyAsJson()).willReturn(
                givenPreBidRequest(builder -> builder.app(App.builder().build())));

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
    public void shouldFailIfRefererCouldNotBeParsed() {
        // given
        httpRequest.headers().set(REFERER, "httpP://non_an_url");

        // when
        final Future<PreBidRequestContext> preBidRequestContextFuture = factory.fromRequest(routingContext);

        // then
        assertThat(preBidRequestContextFuture.failed()).isTrue();
        assertThat(preBidRequestContextFuture.cause())
                .isInstanceOf(PreBidException.class)
                .hasMessage("Invalid URL 'httpP://non_an_url': unknown protocol: httpp")
                .hasCauseInstanceOf(MalformedURLException.class);
    }

    @Test
    public void shouldFailIfRefererDoesNotContainHost() {
        // given
        httpRequest.headers().set(REFERER, "http:/path");

        // when
        final Future<PreBidRequestContext> preBidRequestContextFuture = factory.fromRequest(routingContext);

        // then
        assertThat(preBidRequestContextFuture.failed()).isTrue();
        assertThat(preBidRequestContextFuture.cause())
                .isInstanceOf(PreBidException.class)
                .hasMessage("Host not found from URL 'http:/path'");
    }

    @Test
    public void shouldFailIfDomainCouldNotBeDerivedFromReferer() {
        // given
        httpRequest.headers().set(REFERER, "http://domain");

        // when
        final Future<PreBidRequestContext> preBidRequestContextFuture = factory.fromRequest(routingContext);

        // then
        assertThat(preBidRequestContextFuture.failed()).isTrue();
        assertThat(preBidRequestContextFuture.cause())
                .isInstanceOf(PreBidException.class)
                .hasMessage("Invalid URL 'domain': cannot derive eTLD+1 for domain domain");
    }

    @Test
    public void shouldDeriveRefererAndDomainFromRequestParamIfUrlOverrideParamExists() {
        // given
        given(httpRequest.getParam("url_override")).willReturn("http://exampleoverrride.com");

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.getReferer()).isEqualTo("http://exampleoverrride.com");
        assertThat(preBidRequestContext.getDomain()).isEqualTo("exampleoverrride.com");
    }

    @Test
    public void shouldDeriveRefererAndDomainFromRefererHeaderIfUrlOverrideParamBlank() {
        // given
        given(httpRequest.getParam("url_override")).willReturn("");

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.getReferer()).isEqualTo("http://example.com");
        assertThat(preBidRequestContext.getDomain()).isEqualTo("example.com");
    }

    @Test
    public void shouldPrefixHttpSchemeToUrlIfUrlOverrideParamDoesNotContainScheme() {
        // given
        given(httpRequest.getParam("url_override")).willReturn("example.com");

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.getReferer()).isEqualTo("http://example.com");
        assertThat(preBidRequestContext.getDomain()).isEqualTo("example.com");
    }

    @Test
    public void shouldPrefixHttpSchemeToUrlIfRefererHeaderDoesNotContainScheme() {
        // given
        httpRequest.headers().set(REFERER, "example.com");

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.getReferer()).isEqualTo("http://example.com");
        assertThat(preBidRequestContext.getDomain()).isEqualTo("example.com");
    }

    @Test
    public void shouldSetFieldsFromHeadersIfBodyFieldsEmpty() {
        // given
        httpRequest.headers().set("User-Agent", "UnitTest");
        httpRequest.headers().set("Referer", "http://example.com");
        given(uidsCookieService.parseHostCookie(any())).willReturn("userId");

        // when
        final BidRequest populatedBidRequest = factory.fromRequest(BidRequest.builder().build(), routingContext);

        // then
        assertThat(populatedBidRequest.getSite()).isEqualTo(
                Site.builder().page("http://example.com").domain("example.com").build());
        assertThat(populatedBidRequest.getDevice()).isEqualTo(
                Device.builder().ip("192.168.244.1").ua("UnitTest").build());
        assertThat(populatedBidRequest.getUser()).isEqualTo(User.builder().id("userId").build());
    }

    @Test
    public void shouldNotSetFieldsFromHeadersIfRequestFieldsNotEmpty() {
        // given
        httpRequest.headers().set("User-Agent", "UnitTest");
        httpRequest.headers().set("Referer", "http://example.com");
        given(uidsCookieService.parseHostCookie(any())).willReturn("userId");
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().domain("test.com").page("http://test.com").build())
                .device(Device.builder().ua("UnitTestUA").ip("56.76.12.3").build())
                .user(User.builder().id("userId").build())
                .build();

        // when
        final BidRequest populatedBidRequest = factory.fromRequest(bidRequest, routingContext);

        // then
        assertThat(populatedBidRequest).isSameAs(bidRequest);
    }

    @Test
    public void shouldNotSetSiteIfNoReferer() {
        // given
        given(httpRequest.headers()).willReturn(new CaseInsensitiveHeaders());

        // when
        final BidRequest populatedBidRequest = factory.fromRequest(BidRequest.builder().build(), routingContext);

        // then
        assertThat(populatedBidRequest.getSite()).isNull();
    }

    @Test
    public void shouldNotSetSitePageIfNoReferer() {
        // given
        given(httpRequest.headers()).willReturn(new CaseInsensitiveHeaders());
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().domain("home.com").build())
                .build();

        // when
        final BidRequest result = factory.fromRequest(bidRequest, routingContext);

        // then
        assertThat(result.getSite()).isEqualTo(Site.builder().domain("home.com").build());
    }

    @Test
    public void shouldNotSetSitePageIfRefererCouldNotBeParsed() {
        // given
        httpRequest.headers().set("Referer", "http://.com:50505");
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().domain("home.com").build())
                .build();

        // when
        final BidRequest result = factory.fromRequest(bidRequest, routingContext);

        // then
        assertThat(result.getSite()).isEqualTo(Site.builder().domain("home.com").build());
    }

    @Test
    public void shouldSetDeviceIpFromXForwardedFor() {
        // given
        httpRequest.headers().set("X-Forwarded-For", "203.0.113.195, 70.41.3.18");

        // when
        final BidRequest result = factory.fromRequest(BidRequest.builder().build(), routingContext);

        // then
        assertThat(result.getDevice()).isNotNull();
        assertThat(result.getDevice().getIp()).isEqualTo("203.0.113.195");
    }

    @Test
    public void shouldSetDeviceIpFromXRealIp() {
        // given
        httpRequest.headers().set("X-Real-IP", "54.83.132.159");

        // when
        final BidRequest result = factory.fromRequest(BidRequest.builder().build(), routingContext);

        // then
        assertThat(result.getDevice()).isNotNull();
        assertThat(result.getDevice().getIp()).isEqualTo("54.83.132.159");
    }

    @Test
    public void shouldNotSetUserIfNoHostCookie() {
        // given
        given(uidsCookieService.parseHostCookie(any())).willReturn(null);

        // when
        final BidRequest result = factory.fromRequest(BidRequest.builder().build(), routingContext);

        // then
        assertThat(result.getUser()).isNull();
    }

    private static Bid givenBid(String bidder) {
        return Bid.of(null, bidder, null);
    }

    private JsonObject givenPreBidRequest(Function<PreBidRequestBuilder, PreBidRequestBuilder> builderCustomizer) {
        return JsonObject.mapFrom(builderCustomizer.apply(
                PreBidRequest.builder()
                        .adUnits(singletonList(AdUnit.builder()
                                .bids(singletonList(givenBid(RUBICON)))
                                .build())))
                .build());
    }

    private static ObjectNode rubiconParams(Integer accountId, Integer siteId, Integer zoneId) {
        return mapper.valueToTree(RubiconParams.builder()
                .accountId(accountId)
                .siteId(siteId)
                .zoneId(zoneId)
                .build());
    }
}
