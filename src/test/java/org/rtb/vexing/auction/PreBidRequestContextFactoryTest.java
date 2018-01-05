package org.rtb.vexing.auction;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Format;
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
import org.rtb.vexing.config.ApplicationConfig;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

public class PreBidRequestContextFactoryTest extends VertxTest {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final Long HTTP_REQUEST_TIMEOUT = 250L;
    private static final String RUBICON = "rubicon";
    private static final String APPNEXUS = "appnexus";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private ApplicationConfig config;
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
        given(routingContext.getBodyAsJson()).willReturn(givenPreBidRequestCustomizable(identity()));
        given(routingContext.request()).willReturn(httpRequest);
        given(httpRequest.headers()).willReturn(new CaseInsensitiveHeaders());
        given(httpRequest.remoteAddress()).willReturn(new SocketAddressImpl(0, "192.168.244.1"));
        httpRequest.headers().set(REFERER, "http://example.com");

        // default timeout config
        given(config.getLong(eq("default-timeout-ms"))).willReturn(HTTP_REQUEST_TIMEOUT);

        // parsed uids cookie
        given(uidsCookieService.parseFromRequest(any())).willReturn(uidsCookie);
        given(uidsCookie.hasLiveUids()).willReturn(false);

        factory = PreBidRequestContextFactory.create(config, psl, applicationSettings, uidsCookieService);
    }

    @Test
    public void createShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> PreBidRequestContextFactory.create(null, null, null, null));
        assertThatNullPointerException().isThrownBy(() -> PreBidRequestContextFactory.create(config, null, null, null));
        assertThatNullPointerException().isThrownBy(() -> PreBidRequestContextFactory.create(config, psl, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> PreBidRequestContextFactory.create(config, psl, applicationSettings, null));
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
                .bids(singletonList(Bid.builder().bidder(RUBICON).build()))
                .build();

        given(routingContext.getBodyAsJson()).willReturn(
                givenPreBidRequestCustomizable(builder -> builder.adUnits(Collections.singletonList(adUnit))));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.bidders).hasSize(1);
        assertThat(preBidRequestContext.preBidRequest).isNotNull();
        assertThat(preBidRequestContext.timeout).isEqualTo(HTTP_REQUEST_TIMEOUT);
        assertThat(preBidRequestContext.ip).isEqualTo("192.168.244.1");
        assertThat(preBidRequestContext.secure).isNull();
        assertThat(preBidRequestContext.isDebug).isFalse();
        assertThat(preBidRequestContext.uidsCookie).isNotNull();
        assertThat(preBidRequestContext.noLiveUids).isTrue();
        assertThat(preBidRequestContext.ua).isEqualTo("userAgent");
        assertThat(preBidRequestContext.referer).isEqualTo("http://www.example.com");
        assertThat(preBidRequestContext.domain).isEqualTo("example.com");
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
                .bids(singletonList(Bid.builder()
                        .bidder(RUBICON)
                        .bidId("bidId")
                        .params(rubiconParams(1001, 2001, 3001))
                        .build()))
                .build();
        given(routingContext.getBodyAsJson()).willReturn(
                givenPreBidRequestCustomizable(builder -> builder.adUnits(singletonList(adUnit))));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.bidders).containsOnly(Bidder.from(RUBICON, singletonList(AdUnitBid.builder()
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
                .bids(singletonList(Bid.builder().bidder(RUBICON).build()))
                .build();

        given(routingContext.getBodyAsJson()).willReturn(
                givenPreBidRequestCustomizable(builder -> builder.adUnits(singletonList(adUnit))));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.bidders.get(0).adUnitBids).hasSize(1).element(0)
                .returns(Collections.singleton(MediaType.banner), adUnitBid -> adUnitBid.mediaTypes);
    }

    @Test
    public void shouldPopulateAdUnitBidWithBannerIfMediaTypeIsUnknown() {
        // given
        final AdUnit adUnit = AdUnit.builder()
                .code("adUnitCode1")
                .sizes(singletonList(Format.builder().w(300).h(250).build()))
                .bids(singletonList(Bid.builder().bidder(RUBICON).build()))
                .mediaTypes(Collections.singletonList("RandomMediaType"))
                .build();

        given(routingContext.getBodyAsJson()).willReturn(
                givenPreBidRequestCustomizable(builder -> builder.adUnits(singletonList(adUnit))));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.bidders.get(0).adUnitBids).hasSize(1).element(0)
                .returns(Collections.singleton(MediaType.banner), adUnitBid -> adUnitBid.mediaTypes);
    }

    @Test
    public void shouldPopulateAdUnitWithBannerAndVideoIfBothArePresent() {
        // given
        final AdUnit adUnit = AdUnit.builder()
                .code("adUnitCode1")
                .sizes(singletonList(Format.builder().w(300).h(250).build()))
                .bids(singletonList(Bid.builder().bidder(RUBICON).build()))
                .mediaTypes(asList("banner", "video"))
                .build();

        given(routingContext.getBodyAsJson()).willReturn(
                givenPreBidRequestCustomizable(builder -> builder.adUnits(singletonList(adUnit))));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.bidders.get(0).adUnitBids).hasSize(1).element(0)
                .returns(EnumSet.of(MediaType.banner, MediaType.video), adUnitBid -> adUnitBid.mediaTypes);
    }

    @Test
    public void shouldNotReturnAdUnitsWithNullSizes() {
        //given
        final AdUnit adUnit = AdUnit.builder()
                .bids(singletonList(Bid.builder().bidder(RUBICON).build()))
                .code("AdUnitCode1")
                .build();
        final AdUnit adUnit2 = AdUnit.builder()
                .bids(singletonList(Bid.builder().bidder(RUBICON).build()))
                .code("AdUnitCode2")
                .sizes(singletonList(Format.builder().w(300).h(250).build()))
                .build();

        given(routingContext.getBodyAsJson()).willReturn(
                givenPreBidRequestCustomizable(builder -> builder.adUnits(asList(adUnit, adUnit2))));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.bidders.get(0).adUnitBids).hasSize(1).element(0)
                .returns("AdUnitCode2", adUnitBid -> adUnitBid.adUnitCode);
    }

    @Test
    public void shouldNotReturnAdUnitsWithNullAdUnitCode() {
        //given
        final AdUnit adUnit = AdUnit.builder()
                .bids(singletonList(Bid.builder().bidder(RUBICON).build()))
                .sizes(singletonList(Format.builder().w(300).h(250).build()))
                .build();
        final AdUnit adUnit2 = AdUnit.builder()
                .bids(singletonList(Bid.builder().bidder(RUBICON).build()))
                .code("AdUnitCode2")
                .sizes(singletonList(Format.builder().w(300).h(250).build()))
                .build();

        given(routingContext.getBodyAsJson()).willReturn(
                givenPreBidRequestCustomizable(builder -> builder.adUnits(asList(adUnit, adUnit2))));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.bidders.get(0).adUnitBids).hasSize(1).element(0)
                .returns("AdUnitCode2", adUnitBid -> adUnitBid.adUnitCode);
    }

    @Test
    public void shouldExtractMultipleBidders() {
        // given
        final AdUnit adUnit = AdUnit.builder()
                .code("adUnitCode1")
                .sizes(Collections.singletonList(Format.builder().h(100).w(200).build()))
                .bids(asList(Bid.builder().bidder(RUBICON).build(), Bid.builder().bidder(APPNEXUS).build()))
                .build();
        given(routingContext.getBodyAsJson()).willReturn(
                givenPreBidRequestCustomizable(builder -> builder.adUnits(asList(adUnit, adUnit))));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.bidders).hasSize(2)
                .flatExtracting(bidder -> bidder.adUnitBids).hasSize(4);
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
                givenPreBidRequestCustomizable(builder -> builder.adUnits(singletonList(adUnit))));

        given(applicationSettings.getAdUnitConfigById(anyString()))
                .willReturn(Future.succeededFuture(mapper.writeValueAsString(singletonList(
                        Bid.builder()
                                .bidder(RUBICON)
                                .bidId("bidId")
                                .params(rubiconParams(4001, 5001, 6001))
                                .build()))));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.bidders).containsOnly(Bidder.from(RUBICON, singletonList(AdUnitBid.builder()
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
                givenPreBidRequestCustomizable(builder -> builder.adUnits(singletonList(adUnit))));

        given(applicationSettings.getAdUnitConfigById(anyString()))
                .willReturn(Future.failedFuture(new PreBidException("Not found")));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.bidders).isEmpty();
    }

    @Test
    public void shouldTolerateInvalidAdUnitConfig() {
        // given
        final AdUnit adUnit = AdUnit.builder().configId("configId").build();
        given(routingContext.getBodyAsJson()).willReturn(
                givenPreBidRequestCustomizable(builder -> builder.adUnits(singletonList(adUnit))));

        given(applicationSettings.getAdUnitConfigById(anyString())).willReturn(Future.succeededFuture("invalid"));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.bidders).isEmpty();
    }

    @Test
    public void shouldGenerateBidIdIfAbsentInRequest() {
        // given
        given(routingContext.getBodyAsJson()).willReturn(givenPreBidRequestCustomizable(identity()));

        final AdUnit adUnit = AdUnit.builder()
                .code("adUnitCode1")
                .sizes(Collections.singletonList(Format.builder().w(100).h(100).build()))
                .bids(singletonList(Bid.builder().bidder(RUBICON).build()))
                .build();

        given(routingContext.getBodyAsJson()).willReturn(
                givenPreBidRequestCustomizable(builder -> builder.adUnits(Collections.singletonList(adUnit))));
        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.bidders)
                .flatExtracting(bidder -> bidder.adUnitBids)
                .extracting(adUnitBid -> adUnitBid.bidId)
                .element(0)
                .matches(id -> isDigits(id) && toLong(id) >= 0);
    }

    @Test
    public void shouldPickTimeoutFromRequest() {
        // given
        given(routingContext.getBodyAsJson()).willReturn(
                givenPreBidRequestCustomizable(builder -> builder.timeoutMillis(1000L)));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.timeout).isEqualTo(1000L);
    }

    @Test
    public void shouldPickDefaultTimeoutIfZeroInRequest() {
        // given
        given(routingContext.getBodyAsJson()).willReturn(
                givenPreBidRequestCustomizable(builder -> builder.timeoutMillis(0L)));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.timeout).isEqualTo(HTTP_REQUEST_TIMEOUT);
    }

    @Test
    public void shouldPickDefaultTimeoutIfGreaterThan2000InRequest() {
        // given
        given(routingContext.getBodyAsJson()).willReturn(
                givenPreBidRequestCustomizable(builder -> builder.timeoutMillis(5000L)));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.timeout).isEqualTo(HTTP_REQUEST_TIMEOUT);
    }

    @Test
    public void shouldSetSecureFlagIfXForwardedProtoIsHttps() {
        // given
        httpRequest.headers().set("X-Forwarded-Proto", "https");

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.secure).isEqualTo(1);
    }


    @Test
    public void shouldSetSecureFlagIfConnectedViaSSL() {
        // given
        given(httpRequest.scheme()).willReturn("https");

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.secure).isEqualTo(1);
    }

    @Test
    public void shouldNotSetSecureFlag() {
        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.secure).isNull();
    }

    @Test
    public void shouldSetSingleIpFromXForwardedFor() {
        // given
        httpRequest.headers().set(X_FORWARDED_FOR, " 192.168.144.1 ");

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.ip).isEqualTo("192.168.144.1");
    }

    @Test
    public void shouldSetFirstIpFromXForwardedFor() {
        // given
        httpRequest.headers().set(X_FORWARDED_FOR, " 192.168.44.1 , 192.168.144.1 , 192.168.244.1 ");

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.ip).isEqualTo("192.168.44.1");
    }

    @Test
    public void shouldSetIpFromXRealIP() {
        // given
        httpRequest.headers().set("X-Real-IP", " 192.168.44.1 ");

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.ip).isEqualTo("192.168.44.1");
    }

    @Test
    public void shouldSetIsDebugToTrueIfTrueInPreBidRequest() {
        // given
        given(routingContext.getBodyAsJson()).willReturn(
                givenPreBidRequestCustomizable(builder -> builder.isDebug(true)));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.isDebug).isTrue();
    }

    @Test
    public void shouldSetIsDebugToTrueIfQueryParameterEqualTo1() {
        // given
        given(routingContext.getBodyAsJson()).willReturn(
                givenPreBidRequestCustomizable(builder -> builder.isDebug(false)));
        given(httpRequest.getParam(eq("debug"))).willReturn("1");

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.isDebug).isTrue();
    }

    @Test
    public void shouldSetIsDebugToFalseIfQueryParameterNotEqualTo1() {
        // given
        given(httpRequest.getParam(eq("debug"))).willReturn("2");

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.isDebug).isFalse();
    }

    @Test
    public void shouldNotSetClientDataIfAppPresentInPreBidRequest() {
        // given
        given(routingContext.getBodyAsJson()).willReturn(
                givenPreBidRequestCustomizable(builder -> builder.app(App.builder().build())));

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.uidsCookie).isNull();
        assertThat(preBidRequestContext.noLiveUids).isFalse();
        assertThat(preBidRequestContext.ua).isNull();
        assertThat(preBidRequestContext.referer).isNull();
        assertThat(preBidRequestContext.domain).isNull();
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
        assertThat(preBidRequestContext.referer).isEqualTo("http://exampleoverrride.com");
        assertThat(preBidRequestContext.domain).isEqualTo("exampleoverrride.com");
    }

    @Test
    public void shouldDeriveRefererAndDomainFromRefererHeaderIfUrlOverrideParamBlank() {
        // given
        given(httpRequest.getParam("url_override")).willReturn("");

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.referer).isEqualTo("http://example.com");
        assertThat(preBidRequestContext.domain).isEqualTo("example.com");
    }

    @Test
    public void shouldPrefixHttpSchemeToUrlIfUrlOverrideParamDoesNotContainScheme() {
        // given
        given(httpRequest.getParam("url_override")).willReturn("example.com");

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.referer).isEqualTo("http://example.com");
        assertThat(preBidRequestContext.domain).isEqualTo("example.com");
    }

    @Test
    public void shouldPrefixHttpSchemeToUrlIfRefererHeaderDoesNotContainScheme() {
        // given
        httpRequest.headers().set(REFERER, "example.com");

        // when
        final PreBidRequestContext preBidRequestContext = factory.fromRequest(routingContext).result();

        // then
        assertThat(preBidRequestContext.referer).isEqualTo("http://example.com");
        assertThat(preBidRequestContext.domain).isEqualTo("example.com");
    }

    private JsonObject givenPreBidRequestCustomizable(
            Function<PreBidRequest.PreBidRequestBuilder, PreBidRequest.PreBidRequestBuilder> builderCustomizer) {
        return JsonObject.mapFrom(builderCustomizer.apply(
                PreBidRequest.builder()
                        .adUnits(singletonList(AdUnit.builder()
                                .bids(singletonList(Bid.builder()
                                        .bidder(RUBICON)
                                        .build()))
                                .build())))
                .build());
    }

    private static ObjectNode rubiconParams(Integer accountId, Integer siteId, Integer zoneId) {
        return defaultNamingMapper.valueToTree(RubiconParams.builder()
                .accountId(accountId)
                .siteId(siteId)
                .zoneId(zoneId)
                .build());
    }
}
