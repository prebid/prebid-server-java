package org.prebid.server.bidder.beachfront;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.beachfront.model.BeachfrontBannerRequest;
import org.prebid.server.bidder.beachfront.model.BeachfrontResponseSlot;
import org.prebid.server.bidder.beachfront.model.BeachfrontSize;
import org.prebid.server.bidder.beachfront.model.BeachfrontSlot;
import org.prebid.server.bidder.beachfront.model.BeachfrontVideoRequest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidSchainSchain;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidSchainSchainNode;
import org.prebid.server.proto.openrtb.ext.request.ExtSource;
import org.prebid.server.proto.openrtb.ext.request.beachfront.ExtImpBeachfront;
import org.prebid.server.proto.openrtb.ext.request.beachfront.ExtImpBeachfrontAppIds;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidVideo;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class BeachfrontBidderTest extends VertxTest {

    private static final String BANNER_ENDPOINT = "http://banner-beachfront.com";
    private static final String VIDEO_ENDPOINT = "http://video-beachfront.com?exchange_id=";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private BeachfrontBidder beachfrontBidder;

    @Mock
    private CurrencyConversionService currencyConversionService;

    @Before
    public void setUp() {
        beachfrontBidder =
                new BeachfrontBidder(BANNER_ENDPOINT, VIDEO_ENDPOINT, currencyConversionService, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidBannerUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new BeachfrontBidder(
                "invalid", null, currencyConversionService, jacksonMapper));
    }

    @Test
    public void creationShouldFailOnInvalidVideoUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new BeachfrontBidder(
                BANNER_ENDPOINT, "invalid", null, jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenNoValidImpressions() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().build()))
                .build();

        // when
        final Result<List<HttpRequest<Void>>> result = beachfrontBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("no valid impressions were found in the request"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<Void>>> result = beachfrontBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith(
                "ignoring imp id=123, error while decoding extImpBeachfront, err: Cannot deserialize value");
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenNoAppIdIsPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.ext(mapper.valueToTree(ExtPrebid.of(null,
                        mapper.valueToTree(ExtImpBeachfront.of(null, null, null, null))))));

        // when
        final Result<List<HttpRequest<Void>>> result = beachfrontBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("unable to determine the appId(s) from the supplied extension"));
    }

    @Test
    public void makeHttpRequestsShouldReturnOneBannerOneAdmAndOneNurlRequestsAndSkipInvalidImps() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().build())
                .imp(asList(
                        givenImp(impBuilder -> impBuilder.ext(mapper.valueToTree(ExtPrebid.of(null,
                                mapper.valueToTree(ExtImpBeachfront.of(null, null, BigDecimal.ONE, null)))))),
                        givenImp(identity()),
                        givenImp(impBuilder -> impBuilder.ext(mapper.valueToTree(ExtPrebid.of(null,
                                mapper.valueToTree(ExtImpBeachfront.of("appId", null, BigDecimal.ONE, "nurl")))))),
                        givenImp(impBuilder -> impBuilder
                                .banner(Banner.builder()
                                        .format(singletonList(Format.builder().w(100).h(200).build()))
                                        .build())
                                .video(null)),
                        givenImp(impBuilder -> impBuilder
                                .banner(Banner.builder()
                                        .format(singletonList(Format.builder().w(100).h(300).build()))
                                        .build())
                                .video(null)
                                .ext(mapper.valueToTree(ExtPrebid.of(null,
                                        mapper.valueToTree(ExtImpBeachfront.of(null, null, null, null))))))))
                .build();

        // when
        final Result<List<HttpRequest<Void>>> result = beachfrontBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(2)
                .containsOnly(BidderError.badInput("unable to determine the appId(s) from the supplied extension"));

        final List<HttpRequest<Void>> httpRequests = result.getValue();
        assertThat(httpRequests).hasSize(3);

        assertThat(httpRequests.get(0).getUri()).isEqualTo(BANNER_ENDPOINT);

        assertThat(asList(httpRequests.get(1), httpRequests.get(2)))
                .extracting(HttpRequest::getUri)
                .containsOnly(
                        VIDEO_ENDPOINT + "appId",
                        VIDEO_ENDPOINT + "appId&prebidserver");
    }

    @Test
    public void makeHttpRequestsShouldUseAdmAsDefaultResponseTypeForVideo() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().build())
                .imp(singletonList(
                        givenImp(impBuilder -> impBuilder.ext(mapper.valueToTree(ExtPrebid.of(null,
                                mapper.valueToTree(ExtImpBeachfront.of("appId", null, BigDecimal.ONE, null))))))))
                .build();

        // when
        final Result<List<HttpRequest<Void>>> result = beachfrontBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();

        final List<HttpRequest<Void>> httpRequests = result.getValue();
        assertThat(httpRequests).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsOnly(VIDEO_ENDPOINT + "appId");
    }

    @Test
    public void makeHttpRequestsShouldHaveFallbackPriceIfConversionFails() {
        // given
        when(currencyConversionService.convertCurrency(any(), any(), any(), any())).thenThrow(
                new PreBidException("Unable to convert from currency UAH to desired ad server currency USD"));

        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(
                        givenImp(impBuilder -> impBuilder
                                .bidfloor(BigDecimal.valueOf(150L))
                                .bidfloorcur("UAH")
                                .ext(mapper.valueToTree(
                                        ExtPrebid.of(null, mapper.valueToTree(
                                                ExtImpBeachfront.of("appId", null,
                                                        BigDecimal.ONE, null))))))))
                .build();

        // when
        final Result<List<HttpRequest<Void>>> result = beachfrontBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors())
                .extracting(BidderError::getMessage)
                .containsExactly(
                        "The following error was received from the currency converter while attempting to convert the "
                                + "imp.bidfloor value of 150 from UAH to USD:\nCurrency service was unable to convert "
                                + "currency.\nThe provided value of imp.ext.beachfront.bidfloor,"
                                + " 1 USD is being used as a fallback.");

        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BeachfrontVideoRequest.class))
                .containsExactly(BeachfrontVideoRequest.builder()
                        .appId("appId")
                        .videoResponseType("adm")
                        .request(BidRequest.builder()
                                .imp(singletonList(givenImp(impBuilder -> impBuilder
                                        .id("123")
                                        .video(Video.builder().w(300).h(250).build())
                                        .bidfloor(BigDecimal.ONE)
                                        .bidfloorcur("USD")
                                        .secure(0)
                                        .ext(null))))
                                .cur(singletonList("USD"))
                                .build())
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldConvertToBidderCurrency() {
        // given
        when(currencyConversionService.convertCurrency(any(), any(), any(), any()))
                .thenReturn(BigDecimal.valueOf(5.55D));

        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(
                        givenImp(impBuilder -> impBuilder
                                .bidfloor(BigDecimal.valueOf(150L))
                                .bidfloorcur("UAH")
                                .ext(mapper.valueToTree(
                                        ExtPrebid.of(null, mapper.valueToTree(
                                                ExtImpBeachfront.of("appId", null,
                                                        BigDecimal.ONE, null))))))))
                .build();

        // when
        final Result<List<HttpRequest<Void>>> result = beachfrontBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(0);
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BeachfrontVideoRequest.class))
                .containsExactly(BeachfrontVideoRequest.builder()
                        .appId("appId")
                        .videoResponseType("adm")
                        .request(BidRequest.builder()
                                .imp(singletonList(givenImp(impBuilder -> impBuilder
                                        .id("123")
                                        .video(Video.builder().w(300).h(250).build())
                                        .bidfloor(BigDecimal.valueOf(5.55D))
                                        .bidfloorcur("USD")
                                        .secure(0)
                                        .ext(null))))
                                .cur(singletonList("USD"))
                                .build())
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldThrowExceptionAndSkipCurrentImp() {
        // given
        when(currencyConversionService.convertCurrency(any(), any(), any(), any())).thenThrow(
                new PreBidException("Unable to convert from currency UAH to desired ad server currency USD"));

        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(
                        givenImp(impBuilder -> impBuilder
                                .bidfloor(BigDecimal.valueOf(150L))
                                .bidfloorcur("UAH")
                                .ext(mapper.valueToTree(
                                        ExtPrebid.of(null, mapper.valueToTree(
                                                ExtImpBeachfront.of("appId", null,
                                                        BigDecimal.ZERO, null))))))))
                .build();

        // when
        final Result<List<HttpRequest<Void>>> result = beachfrontBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .extracting(BidderError::getMessage)
                .containsExactly(
                        "The following error was received from the currency converter while attempting to convert the "
                                + "imp.bidfloor value of 150 from UAH to USD:\nCurrency service was unable to convert "
                                + "currency.\nA value of imp.ext.beachfront.bidfloor was not provided. "
                                + "The bid is being skipped.");
    }

    @Test
    public void makeHttpRequestsShouldFilterBannerImpWithoutFormat() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().build())
                .imp(singletonList(
                        givenImp(impBuilder -> impBuilder
                                .banner(Banner.builder().w(200).h(400).build())
                                .video(null))))
                .build();

        // when
        final Result<List<HttpRequest<Void>>> result = beachfrontBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("no valid impressions were found in the request"));
    }

    @Test
    public void makeHttpRequestsShouldReturnRequestWithExpectedBasicHeadersAndAdditionalDeviceHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                requestBuilder -> requestBuilder.device(Device.builder()
                        .ua("some_agent")
                        .language("albanian")
                        .dnt(1)
                        .build()));

        // when
        final Result<List<HttpRequest<Void>>> result = beachfrontBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue().get(0).getHeaders())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), "application/json;charset=utf-8"),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), "application/json"),
                        tuple(HttpUtil.USER_AGENT_HEADER.toString(), "some_agent"),
                        tuple(HttpUtil.ACCEPT_LANGUAGE_HEADER.toString(), "albanian"),
                        tuple(HttpUtil.DNT_HEADER.toString(), "1"));
    }

    @Test
    public void makeHttpRequestsShouldAdditionalCookieHeaderForVideoRequestWhenBuyerUidIsPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                identity(),
                requestBuilder -> requestBuilder.user(User.builder().buyeruid("4125").build()));

        // when
        final Result<List<HttpRequest<Void>>> result = beachfrontBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue().get(0).getHeaders())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .contains(
                        tuple(HttpUtil.COOKIE_HEADER.toString(), "__io_cid=4125"));
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedBannerRequestWithSchain() {
        // given
        final ExtRequestPrebidSchainSchainNode globalNode = ExtRequestPrebidSchainSchainNode.of(
                "pbshostcompany.com", "00001", null, null, null, null, null);
        final ExtRequestPrebidSchainSchain expectedSchain = ExtRequestPrebidSchainSchain.of(
                null, null, singletonList(globalNode), null);

        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .video(null)
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(100).h(300).build()))
                                .build()),
                requestBuilder -> requestBuilder
                        .source(Source.builder().ext(ExtSource.of(expectedSchain)).build())
        );

        // when
        final Result<List<HttpRequest<Void>>> result = beachfrontBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BeachfrontBannerRequest.class))
                .extracting(BeachfrontBannerRequest::getSchain)
                .containsExactly(expectedSchain);
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedBannerRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .video(null)
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(100).h(300).build()))
                                .build())
                        .secure(1),
                requestBuilder -> requestBuilder
                        .user(User.builder().id("userId").buyeruid("buid").build())
                        .device(Device.builder().model("3310").os("nokia").build()));

        // when
        final Result<List<HttpRequest<Void>>> result = beachfrontBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BeachfrontBannerRequest.class))
                .containsOnly(BeachfrontBannerRequest.builder()
                        .slots(singletonList(BeachfrontSlot.of("123", "appId", BigDecimal.ONE,
                                singletonList(BeachfrontSize.of(100, 300)))))
                        .secure(1)
                        .deviceModel("3310")
                        .deviceOs("nokia")
                        .isMobile(1)
                        .user(User.builder().id("userId").buyeruid("buid").build())
                        .adapterVersion("1.0.0")
                        .adapterName("BF_PREBID_S2S")
                        .requestId("153")
                        .real204(true)
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedAdmAndNurlVideoRequests() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().bundle("prefix_test1.test2.test3_suffix").build())
                .device(Device.builder().build())
                .imp(asList(
                        givenImp(impBuilder -> impBuilder.ext(mapper.valueToTree(ExtPrebid.of(null,
                                mapper.valueToTree(ExtImpBeachfront.of("appId2", null, BigDecimal.TEN, "nurl")))))),
                        givenImp(impBuilder -> impBuilder.id("234"))))
                .build();

        // when
        final Result<List<HttpRequest<Void>>> result = beachfrontBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();

        final Imp.ImpBuilder expectedImpBuilder = Imp.builder()
                .video(Video.builder().w(300).h(250).build())
                .secure(0);

        assertThat(result.getValue()).hasSize(2)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BeachfrontVideoRequest.class))
                .containsOnly(BeachfrontVideoRequest.builder()
                                .isPrebid(true)
                                .appId("appId2")
                                .videoResponseType("nurl")
                                .request(BidRequest.builder()
                                        .device(Device.builder().devicetype(1).build())
                                        .app(App.builder().bundle("prefix_test1.test2.test3_suffix")
                                                .domain("test2.prefix_test1").build())
                                        .imp(singletonList(expectedImpBuilder.id("123")
                                                .bidfloor(BigDecimal.TEN).bidfloorcur("USD").build()))
                                        .cur(singletonList("USD"))
                                        .build())
                                .build(),
                        BeachfrontVideoRequest.builder()
                                .appId("appId")
                                .videoResponseType("adm")
                                .request(BidRequest.builder()
                                        .device(Device.builder().ip("255.255.255.255").devicetype(1).build())
                                        .app(App.builder().bundle("prefix_test1.test2.test3_suffix")
                                                .domain("test2.prefix_test1").build())
                                        .imp(singletonList(expectedImpBuilder.id("234")
                                                .bidfloor(BigDecimal.ONE).bidfloorcur("USD").build()))
                                        .cur(singletonList("USD"))
                                        .build())
                                .build());
    }

    @Test
    public void makeHttpRequestsShouldCreateAdmRequestForEveryUnknownResponseType() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(
                        givenImp(impBuilder -> impBuilder.ext(mapper.valueToTree(ExtPrebid.of(null,
                                mapper.valueToTree(ExtImpBeachfront.of("appId2", null, null, "unknownType"))))))
                ))
                .build();

        // when
        final Result<List<HttpRequest<Void>>> result = beachfrontBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BeachfrontVideoRequest.class))
                .extracting(BeachfrontVideoRequest::getVideoResponseType)
                .containsExactly("adm");
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedBidFloorFromBidRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .video(Video.builder().w(1).h(1).build())
                        .bidfloor(BigDecimal.ONE)
                        .secure(1));

        // when
        final Result<List<HttpRequest<Void>>> result = beachfrontBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BeachfrontVideoRequest.class))
                .extracting(BeachfrontVideoRequest::getRequest)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor)
                .containsExactly(BigDecimal.ONE);
    }

    @Test
    public void makeHttpRequestsShouldUseDefaultBidFloorIfNoInRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpBeachfront.of("appId",
                                        ExtImpBeachfrontAppIds.of("videoIds", "bannerIds"),
                                        null, "adm"))))
                        .video(Video.builder().w(1).h(1).build())
                        .secure(1));

        // when
        final Result<List<HttpRequest<Void>>> result = beachfrontBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BeachfrontVideoRequest.class))
                .extracting(BeachfrontVideoRequest::getRequest)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor)
                .containsExactly(BigDecimal.ZERO);
    }

    @Test
    public void makeBidsShouldReturnEmptyResultWhenResponseBodyHasEmptyArray() {
        // given
        final HttpCall<Void> httpCall = givenHttpCall(null, "[]");

        // when
        final Result<List<BidderBid>> result = beachfrontBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseBodyIsInvalid() {
        // given
        final HttpCall<Void> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = beachfrontBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage())
                .isEqualTo("server response failed to unmarshal as valid rtb. "
                        + "Run with request.debug = 1 for more info");
    }

    @Test
    public void makeBidsShouldReturnExpectedBannerBid() throws JsonProcessingException {
        // given
        final BeachfrontResponseSlot responseSlot = BeachfrontResponseSlot.builder()
                .slot("first_slot")
                .crid("CrId")
                .price(3.14f)
                .adm("Adm")
                .w(300)
                .h(450)
                .build();

        final HttpCall<Void> httpCall = givenHttpCall(null, mapper.writeValueAsString(singletonList(responseSlot)));

        // when
        final Result<List<BidderBid>> result = beachfrontBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(
                        Bid.builder()
                                .id("first_slotBanner")
                                .impid("first_slot")
                                .price(BigDecimal.valueOf(3.14f))
                                .adm("Adm")
                                .crid("CrId")
                                .w(300)
                                .h(450)
                                .build(), BidType.banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnEmptyResultWhenResponseHasEmptySeatBids() throws JsonProcessingException {
        // given
        final HttpCall<Void> httpCall = givenHttpCall(
                mapper.writeValueAsBytes(BeachfrontVideoRequest.builder().build()),
                mapper.writeValueAsString(BidResponse.builder().id("some_id").build()));

        // when
        final Result<List<BidderBid>> result = beachfrontBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnExpectedNurlVideoBid() throws JsonProcessingException {
        // given
        final BidResponse bidResponse = givenBidResponse(bidBuilder -> bidBuilder.nurl("nurl:1:2"));
        final BeachfrontVideoRequest videoRequest = BeachfrontVideoRequest.builder()
                .request(BidRequest.builder()
                        .imp(singletonList(Imp.builder()
                                .id("123")
                                .video(Video.builder().w(640).h(480).build())
                                .build()))
                        .build())
                .build();

        final HttpCall<Void> httpCall = HttpCall.success(
                HttpRequest.<Void>builder()
                        .body(mapper.writeValueAsBytes(videoRequest))
                        .uri("url&prebidserver").build(),
                HttpResponse.of(200, null, mapper.writeValueAsString(bidResponse)), null);

        // when
        final Result<List<BidderBid>> result = beachfrontBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(
                        Bid.builder()
                                .id("123NurlVideo")
                                .impid("123")
                                .nurl("nurl:1:2")
                                .crid("2")
                                .w(640)
                                .h(480)
                                .build(), BidType.video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnExpectedAdmVideoBid() throws JsonProcessingException {
        // given
        final BidResponse bidResponse = givenBidResponse(bidBuilder -> bidBuilder.impid("imp1"));
        final BeachfrontVideoRequest videoRequest = BeachfrontVideoRequest.builder()
                .request(BidRequest.builder()
                        .imp(singletonList(Imp.builder().build()))
                        .build())
                .build();

        final HttpCall<Void> httpCall = HttpCall.success(
                HttpRequest.<Void>builder()
                        .body(mapper.writeValueAsBytes(videoRequest))
                        .uri("url").build(),
                HttpResponse.of(200, null, mapper.writeValueAsString(bidResponse)), null);

        // when
        final Result<List<BidderBid>> result = beachfrontBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(
                        Bid.builder()
                                .id("imp1AdmVideo")
                                .impid("imp1")
                                .build(), BidType.video, "USD"));
    }

    @Test
    public void makeBidsShouldEnrichBidsExtWithDurationIfDurationIsPresentInBidExt() throws JsonProcessingException {
        // given
        final BidResponse bidResponse = givenBidResponse(
                bidBuilder -> bidBuilder
                        .impid("imp1")
                        .ext(mapper.createObjectNode().set("duration", IntNode.valueOf(1234))));

        final BeachfrontVideoRequest videoRequest =
                BeachfrontVideoRequest.builder().request(givenBidRequest(identity())).build();

        final HttpCall<Void> httpCall = givenHttpCall(videoRequest, bidResponse);

        // when
        final Result<List<BidderBid>> result = beachfrontBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getExt)
                .containsExactly(givenBidExt(1234, null));
    }

    @Test
    public void makeBidsShouldEnrichBidsExtWithPrimaryCategoryIfDurationAndCategoryIsPresentInBidExt()
            throws JsonProcessingException {
        // given
        final BidResponse bidResponse = givenBidResponse(
                bidBuilder -> bidBuilder
                        .impid("imp1")
                        .cat(List.of("cat1", "cat2"))
                        .ext(mapper.createObjectNode().set("duration", IntNode.valueOf(1234))));

        final BeachfrontVideoRequest videoRequest =
                BeachfrontVideoRequest.builder().request(givenBidRequest(identity())).build();

        final HttpCall<Void> httpCall = givenHttpCall(videoRequest, bidResponse);

        // when
        final Result<List<BidderBid>> result = beachfrontBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getExt)
                .containsExactly(givenBidExt(1234, "cat1"));
    }

    @Test
    public void makeBidsShouldNotModifyBidExtIfDurationIfAbsentInBidExt() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = mapper.createObjectNode();
        final BidResponse bidResponse = givenBidResponse(
                bidBuilder -> bidBuilder
                        .impid("imp1")
                        .cat(List.of("cat1", "cat2"))
                        .ext(bidExt));

        final BeachfrontVideoRequest videoRequest =
                BeachfrontVideoRequest.builder().request(givenBidRequest(identity())).build();

        final HttpCall<Void> httpCall = givenHttpCall(videoRequest, bidResponse);

        // when
        final Result<List<BidderBid>> result = beachfrontBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getExt)
                .containsExactly(bidExt);
    }

    @Test
    public void makeBidsShouldNotModifyBidExtIfDurationIsLessThanZero() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = mapper.createObjectNode().set("duration", IntNode.valueOf(-1));
        final BidResponse bidResponse = givenBidResponse(
                bidBuilder -> bidBuilder
                        .impid("imp1")
                        .cat(List.of("cat1", "cat2"))
                        .ext(bidExt));

        final BeachfrontVideoRequest videoRequest =
                BeachfrontVideoRequest.builder().request(givenBidRequest(identity())).build();

        final HttpCall<Void> httpCall = givenHttpCall(videoRequest, bidResponse);

        // when
        final Result<List<BidderBid>> result = beachfrontBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getExt)
                .containsExactly(bidExt);
    }

    @Test
    public void makeBidsShouldNotModifyBidExtIfDurationIsEqualToZero() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = mapper.createObjectNode().set("duration", IntNode.valueOf(0));
        final BidResponse bidResponse = givenBidResponse(
                bidBuilder -> bidBuilder
                        .impid("imp1")
                        .cat(List.of("cat1", "cat2"))
                        .ext(bidExt));

        final BeachfrontVideoRequest videoRequest =
                BeachfrontVideoRequest.builder().request(givenBidRequest(identity())).build();

        final HttpCall<Void> httpCall = givenHttpCall(videoRequest, bidResponse);

        // when
        final Result<List<BidderBid>> result = beachfrontBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getExt)
                .containsExactly(bidExt);
    }

    @Test
    public void makeBidsShouldNotModifyBidExtIfDurationIsNotInteger() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = mapper.createObjectNode().set("duration", mapper.createArrayNode());
        final BidResponse bidResponse = givenBidResponse(
                bidBuilder -> bidBuilder
                        .impid("imp1")
                        .cat(List.of("cat1", "cat2"))
                        .ext(bidExt));

        final BeachfrontVideoRequest videoRequest =
                BeachfrontVideoRequest.builder().request(givenBidRequest(identity())).build();

        final HttpCall<Void> httpCall = givenHttpCall(videoRequest, bidResponse);

        // when
        final Result<List<BidderBid>> result = beachfrontBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getExt)
                .containsExactly(bidExt);
    }

    @Test
    public void makeBidsShouldNotModifyBidExtIfBidExtIsAbsent() throws JsonProcessingException {
        // given
        final BidResponse bidResponse = givenBidResponse(
                bidBuilder -> bidBuilder
                        .impid("imp1")
                        .cat(List.of("cat1", "cat2")));

        final BeachfrontVideoRequest videoRequest =
                BeachfrontVideoRequest.builder().request(givenBidRequest(identity())).build();

        final HttpCall<Void> httpCall = givenHttpCall(videoRequest, bidResponse);

        // when
        final Result<List<BidderBid>> result = beachfrontBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getExt).hasSize(1)
                .containsNull();
    }

    private static BidRequest givenBidRequest(
            UnaryOperator<Imp.ImpBuilder> impCustomizer,
            UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer) {

        return bidRequestCustomizer.apply(
                        BidRequest.builder()
                                .id("153")
                                .app(App.builder().build())
                                .imp(List.of(givenImp(impCustomizer))))
                .build();
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(impCustomizer, identity());
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("123")
                        .video(Video.builder().build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpBeachfront.of("appId", ExtImpBeachfrontAppIds.of("videoIds", "bannerIds"),
                                        BigDecimal.ONE, "adm")))))
                .build();
    }

    private static BidResponse givenBidResponse(UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
    }

    private static HttpCall<Void> givenHttpCall(BeachfrontVideoRequest beachfrontVideoRequest,
                                                BidResponse bidResponse) throws JsonProcessingException {
        return givenHttpCall(mapper.writeValueAsBytes(beachfrontVideoRequest), mapper.writeValueAsString(bidResponse));
    }

    private static HttpCall<Void> givenHttpCall(byte[] requestBody, String responseBody) {
        return HttpCall.success(
                HttpRequest.<Void>builder().body(requestBody).uri("url").build(),
                HttpResponse.of(200, null, responseBody), null);
    }

    private static ObjectNode givenBidExt(Integer duration, String primaryCategory) {
        final ExtBidPrebid extBidPrebid = ExtBidPrebid.builder()
                .video(ExtBidPrebidVideo.of(duration, primaryCategory))
                .build();
        return mapper.valueToTree(ExtPrebid.of(extBidPrebid, null));
    }
}
