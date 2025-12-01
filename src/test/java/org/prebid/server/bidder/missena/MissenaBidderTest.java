package org.prebid.server.bidder.missena;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.SupplyChain;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.missena.ExtImpMissena;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.version.PrebidVersionProvider;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.prebid.server.util.HttpUtil.ACCEPT_HEADER;
import static org.prebid.server.util.HttpUtil.APPLICATION_JSON_CONTENT_TYPE;
import static org.prebid.server.util.HttpUtil.CONTENT_TYPE_HEADER;
import static org.prebid.server.util.HttpUtil.REFERER_HEADER;
import static org.prebid.server.util.HttpUtil.USER_AGENT_HEADER;
import static org.prebid.server.util.HttpUtil.X_FORWARDED_FOR_HEADER;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;

@ExtendWith(MockitoExtension.class)
class MissenaBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test-url.com/?t={{PublisherID}}";
    private static final String TEST_PBS_VERSION = "pbs-java/1.0";

    @Mock(strictness = LENIENT)
    private CurrencyConversionService currencyConversionService;

    @Mock(strictness = LENIENT)
    private PrebidVersionProvider prebidVersionProvider;

    private MissenaBidder target;

    @BeforeEach
    public void setUp() {
        target = new MissenaBidder(
                ENDPOINT_URL,
                jacksonMapper,
                currencyConversionService,
                prebidVersionProvider);

        given(prebidVersionProvider.getNameVersionRecord()).willReturn(TEST_PBS_VERSION);
        given(currencyConversionService.convertCurrency(any(), any(), anyString(), anyString()))
                .willAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.ext(givenInvalidImpExt()));

        // when
        final Result<List<HttpRequest<MissenaAdRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("Error parsing missenaExt parameters"));
    }

    @Test
    public void makeHttpRequestsShouldMakeRequestForFirstValidImp() {
        // given
        final ObjectNode settingsNode = mapper.createObjectNode().put("settingKey", "settingValue");

        final BidRequest bidRequest = BidRequest.builder()
                .id("requestId")
                .tmax(500L)
                .cur(singletonList("USD"))
                .imp(List.of(
                        givenImp(imp -> imp.id("impId1")
                                .ext(givenImpExt("apiKey1", "placementId1", "1", List.of("banner"), settingsNode))),
                        givenImp(imp -> imp.id("impId2")
                                .ext(givenImpExt("apiKey2", "placementId2", "0", null, null)))))
                .site(Site.builder().page("http://test.com/page").domain("test.com").build())
                .regs(Regs.builder().ext(ExtRegs.of(1, null, null, null)).build())
                .user(User.builder().buyeruid("buyer1")
                        .ext(ExtUser.builder().consent("consentStr").build()).build())
                .source(Source.builder().schain(SupplyChain.of(1, null, null, null)).build())
                .device(Device.builder().ua("test-ua").ip("123.123.123.123").build())
                .build();

        // when
        final Result<List<HttpRequest<MissenaAdRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final MissenaUserParams expectedUserParams = MissenaUserParams.builder()
                .formats(List.of("banner"))
                .placement("placementId1")
                .testMode("1")
                .settings(settingsNode)
                .build();

        final MissenaAdRequest expectedPayload = MissenaAdRequest.builder()
                .adUnit("impId1")
                .currency("USD")
                .floor(BigDecimal.valueOf(0.1))
                .floorCurrency("USD")
                .idempotencyKey("requestId")
                .requestId("requestId")
                .timeout(500L)
                .params(expectedUserParams)
                .version(TEST_PBS_VERSION)
                .bidRequest(bidRequest)
                .build();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .containsExactly(expectedPayload);
        assertThat(result.getValue())
                .extracting(HttpRequest::getImpIds)
                .containsExactly(Collections.singleton("impId1"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfAllImpsAreInvalid() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.ext(givenInvalidImpExt()),
                imp -> imp.ext(givenInvalidImpExt()));

        // when
        final Result<List<HttpRequest<MissenaAdRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(2)
                .extracting(BidderError::getMessage)
                .containsOnly("Error parsing missenaExt parameters");
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedHeadersWhenDeviceHasIpAndIpv6() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity())
                .toBuilder()
                .site(Site.builder().page("http://page.com").build())
                .device(Device.builder().ua("ua").ip("ip").ipv6("ipv6").build())
                .build();

        // when
        final Result<List<HttpRequest<MissenaAdRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getHeaders)
                .satisfies(headers -> {
                    assertThat(headers.get(CONTENT_TYPE_HEADER)).isEqualTo(APPLICATION_JSON_CONTENT_TYPE);
                    assertThat(headers.get(ACCEPT_HEADER)).isEqualTo(APPLICATION_JSON_VALUE);
                    assertThat(headers.get(USER_AGENT_HEADER)).isEqualTo("ua");
                    assertThat(headers.getAll(X_FORWARDED_FOR_HEADER)).containsExactlyInAnyOrder("ip", "ipv6");
                    assertThat(headers.get(REFERER_HEADER)).isEqualTo("http://page.com");
                    assertThat(headers.get(HttpUtil.ORIGIN_HEADER)).isEqualTo("http://page.com");
                });
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedHeadersWhenDeviceHasIpv6Only() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity())
                .toBuilder()
                .device(Device.builder().ip(null).ipv6("ipv6").build())
                .build();

        // when
        final Result<List<HttpRequest<MissenaAdRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getHeaders)
                .satisfies(headers -> {
                    assertThat(headers.get(CONTENT_TYPE_HEADER)).isEqualTo(APPLICATION_JSON_CONTENT_TYPE);
                    assertThat(headers.get(ACCEPT_HEADER)).isEqualTo(APPLICATION_JSON_VALUE);
                    assertThat(headers.get(USER_AGENT_HEADER)).isNull();
                    assertThat(headers.getAll(X_FORWARDED_FOR_HEADER)).containsExactly("ipv6");
                    assertThat(headers.get(REFERER_HEADER)).isNull();
                });
    }

    @Test
    public void makeHttpRequestsShouldConvertBidFloorCurrency() {
        // given
        final Imp imp = givenImp(i -> i.bidfloor(BigDecimal.TEN).bidfloorcur("EUR")
                .ext(givenImpExt("key1")));
        final BidRequest bidRequest = BidRequest.builder().id("reqId").tmax(1000L)
                .imp(singletonList(imp))
                .cur(singletonList("USD"))
                .build();

        // Specific mock for this test, overrides the general one in setUp
        given(currencyConversionService.convertCurrency(BigDecimal.TEN, bidRequest, "EUR", "USD"))
                .willReturn(BigDecimal.valueOf(12));

        // when
        final Result<List<HttpRequest<MissenaAdRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().getFirst().getPayload())
                .extracting(MissenaAdRequest::getFloor, MissenaAdRequest::getFloorCurrency)
                .containsExactly(BigDecimal.valueOf(12), "USD");
    }

    @Test
    public void makeHttpRequestsShouldUseCorrectUri() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.ext(givenImpExt("testApiKey")));

        // when
        final Result<List<HttpRequest<MissenaAdRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://test-url.com/?t=testApiKey");
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<MissenaAdRequest> httpCall = givenHttpCall("invalid");
        final BidRequest bidRequest = givenBidRequest(imp -> imp.id("impId1"));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1).allSatisfy(error -> {
            assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
            assertThat(error.getMessage()).startsWith("Failed to decode");
        });
    }

    @Test
    public void makeBidsShouldReturnSingleBid() throws JsonProcessingException {
        // given
        final MissenaAdResponse bidResponse = MissenaAdResponse.builder()
                .requestId("id")
                .cpm(BigDecimal.TEN)
                .currency("USD")
                .ad("adm")
                .build();

        final BidderCall<MissenaAdRequest> httpCall = givenHttpCall(mapper.writeValueAsString(bidResponse));
        final BidRequest bidRequest = givenBidRequest(imp -> imp.id("impId")).toBuilder().id("requestId").build();

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();

        final Bid expectedBid = Bid.builder()
                .id("requestId")
                .impid("impId")
                .price(BigDecimal.TEN)
                .adm("adm")
                .crid("id")
                .build();

        assertThat(result.getValue()).hasSize(1)
                .containsOnly(BidderBid.of(expectedBid, BidType.banner, "USD"));
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder>... impCustomizers) {
        final List<Imp> imps = Arrays.stream(impCustomizers)
                .map(MissenaBidderTest::givenImp)
                .toList();
        return BidRequest.builder().imp(imps).cur(singletonList("USD")).build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("impId")
                        .bidfloor(BigDecimal.valueOf(0.1))
                        .bidfloorcur("USD")
                        .ext(givenImpExt("defaultApiKey")))
                .build();
    }

    private static ObjectNode givenImpExt(String apiKey) {
        return givenImpExt(apiKey, null, null, null, null);
    }

    private static ObjectNode givenImpExt(String apiKey,
                                          String placement,
                                          String testMode,
                                          List<String> formats,
                                          ObjectNode settings) {

        final ExtImpMissena extImpMissena = ExtImpMissena.builder()
                .apiKey(apiKey)
                .placement(placement)
                .testMode(testMode)
                .formats(formats)
                .settings(settings)
                .build();

        return mapper.valueToTree(ExtPrebid.of(null, extImpMissena));
    }

    private static ObjectNode givenInvalidImpExt() {
        return mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()));
    }

    private static BidderCall<MissenaAdRequest> givenHttpCall(String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<MissenaAdRequest>builder().build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
