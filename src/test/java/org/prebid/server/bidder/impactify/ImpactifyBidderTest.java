package org.prebid.server.bidder.impactify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.MultiMap;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.impactify.ExtImpImpactify;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

public class ImpactifyBidderTest extends VertxTest {

    private static final String TEST_ENDPOINT = "https://test.endpoint.com";
    private static final String INCORRECT_TEST_ENDPOINT = "incorrect.endpoint";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private ImpactifyBidder impactifyBidder;

    @Mock
    private CurrencyConversionService currencyConversionService;

    @Before
    public void setUp() {
        impactifyBidder = new ImpactifyBidder(TEST_ENDPOINT, jacksonMapper, currencyConversionService);
    }

    @Test
    public void createBidderWithWrongEndpointShouldThrowException() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ImpactifyBidder(INCORRECT_TEST_ENDPOINT,
                jacksonMapper, currencyConversionService));
    }

    private static Imp givenImpressionWithValidBannerData() {
        return Imp.builder()
                .id("123")
                .bidfloorcur("USD")
                .bidfloor(BigDecimal.ONE)
                .banner(Banner.builder().build())
                .ext(mapper.valueToTree(ExtPrebid.of(
                        ExtImpImpactify.of("appId", "format", "style"), null)))
                .build();
    }

    private static Imp givenImpressionWithValidVideoData() {
        return Imp.builder()
                .id("123")
                .bidfloorcur("USD")
                .bidfloor(BigDecimal.ONE)
                .video(Video.builder().build())
                .ext(mapper.valueToTree(ExtPrebid.of(
                        ExtImpImpactify.of("appId", "format", "style"), null)))
                .build();
    }

    private static Imp givenImpressionWithNoValidBidType() {
        return Imp.builder()
                .id("123")
                .bidfloorcur("USD")
                .bidfloor(BigDecimal.ONE)
                .audio(Audio.builder().build())
                .ext(mapper.valueToTree(ExtPrebid.of(
                        ExtImpImpactify.of("appId", "format", "style"), null)))
                .build();
    }

    private static Imp givenImpressionWithInvalidExt() {
        return Imp.builder()
                .id("123")
                .bidfloorcur("USD")
                .bidfloor(BigDecimal.ZERO)
                .banner(Banner.builder().build())
                .video(Video.builder().build())
                .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))
                .build();
    }

    @Test
    public void makeHttpRequestsShouldCheckIfImpressionHasCorrectBidFloorAndBidFloorCurChangeItOtherwise() {
        // given
        given(currencyConversionService.convertCurrency(any(), any(), anyString(), anyString()))
                .willReturn(BigDecimal.TEN);
        final BidRequest bidRequest = BidRequest.builder()
                .imp(List.of(givenImpressionWithNonBidderCurrency()))
                .build();

        //when
        Result<List<HttpRequest<BidRequest>>> result = impactifyBidder.makeHttpRequests(bidRequest);

        //then
        assertThat(result.getErrors()).hasSize(0);
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor, Imp::getBidfloorcur)
                .containsExactly(tuple(BigDecimal.TEN, "USD"));
    }

    private static Imp givenImpressionWithNonBidderCurrency() {
        return Imp.builder()
                .id("123")
                .bidfloorcur("EUR")
                .bidfloor(BigDecimal.ONE)
                .banner(Banner.builder().build())
                .video(Video.builder().build())
                .ext(mapper.valueToTree(ExtPrebid.of(
                        ExtImpImpactify.of("appId", "format", "style"), null)))
                .build();
    }

    private static HttpCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return HttpCall.success(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }

    private static BidResponse givenBidResponseWithBidWithValidImpId() {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(Bid.builder().impid("123").build()))
                        .build()))
                .build();
    }

    private static BidResponse givenBidResponseWithBidWithInvalidImpId() {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(Bid.builder().impid("321").build()))
                        .build()))
                .build();
    }

    private static BidResponse givenBidResponseWithEmptySeatBid() {
        return BidResponse.builder()
                .seatbid(emptyList())
                .build();
    }

    @Test
    public void makeHttpRequestsShouldCheckIfValidDataInImpressionHasCorrectBidFloorAndBidFloorCur() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(List.of(givenImpressionWithValidBannerData()))
                .build();

        //when
        Result<List<HttpRequest<BidRequest>>> result = impactifyBidder.makeHttpRequests(bidRequest);

        //then
        assertThat(result.getErrors()).hasSize(0);
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor, Imp::getBidfloorcur)
                .containsExactly(tuple(BigDecimal.ONE, "USD"));
    }

    @Test
    public void makeHttpRequestsWithInvalidImpressionExtWillReturnWithError() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(List.of(givenImpressionWithInvalidExt()))
                .build();

        //when
        Result<List<HttpRequest<BidRequest>>> result = impactifyBidder.makeHttpRequests(bidRequest);

        //then
        assertThat(result.getErrors()).hasSize(1)
                .extracting(BidderError::getMessage)
                .containsExactly("Unable to decode the impression ext for id: 123");
    }

    @Test
    public void makeHttpRequestsWithNoImpressionsWillReturnWithError() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(emptyList())
                .build();

        //when
        Result<List<HttpRequest<BidRequest>>> result = impactifyBidder.makeHttpRequests(bidRequest);

        //then
        assertThat(result.getErrors()).hasSize(1)
                .extracting(BidderError::getMessage)
                .containsExactly("No valid impressions in the bid request");
    }

    @Test
    public void makeHttpRequestsWithValidDataWillThrowExceptionOnCurrencyConversion() {
        // given
        given(currencyConversionService.convertCurrency(any(), any(), anyString(), anyString()))
                .willThrow(PreBidException.class);
        final BidRequest bidRequest = BidRequest.builder()
                .imp(List.of(givenImpressionWithNonBidderCurrency()))
                .build();

        //when
        Result<List<HttpRequest<BidRequest>>> result = impactifyBidder.makeHttpRequests(bidRequest);

        //then
        assertThat(result.getErrors()).hasSize(1)
                .extracting(BidderError::getMessage)
                .containsExactly("Unable to convert provided bid floor currency from EUR to USD for imp `123`");

    }

    @Test
    public void makeHttpRequestsShouldReturnValidBidResponseWithAllHeadersExceptIpv6() {
        //given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(List.of(givenImpressionWithValidBannerData()))
                .device(Device.builder()
                        .ip("ip")
                        .ipv6("ipv6")
                        .ua("ua")
                        .build())
                .site(Site.builder()
                        .page("https://proper.web.site")
                        .build())
                .user(User.builder()
                        .buyeruid("buyer_user_uid")
                        .build())
                .build();

        //when
        final Result<List<HttpRequest<BidRequest>>> result = impactifyBidder.makeHttpRequests(bidRequest);

        //then
        assertThat(result.getErrors()).hasSize(0);
        assertThat(result.getValue())
                .extracting(HttpRequest::getHeaders)
                .flatExtracting(MultiMap::entries)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()),
                        tuple(HttpUtil.USER_AGENT_HEADER.toString(), "ua"),
                        tuple(HttpUtil.X_FORWARDED_FOR_HEADER.toString(), "ip"),
                        tuple(HttpUtil.X_OPENRTB_VERSION_HEADER.toString(), "2.5"),
                        tuple(HttpUtil.REFERER_HEADER.toString(), "https://proper.web.site"),
                        tuple(HttpUtil.COOKIE_HEADER.toString(), "uids=buyer_user_uid")
                );
    }

    @Test
    public void makeHttpRequestsShouldReturnValidBidResponseWithAllHeadersExceptIp() {
        //given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(List.of(givenImpressionWithValidBannerData()))
                .device(Device.builder()
                        .ipv6("ipv6")
                        .build())
                .build();

        //when
        final Result<List<HttpRequest<BidRequest>>> result = impactifyBidder.makeHttpRequests(bidRequest);

        //then
        assertThat(result.getErrors()).hasSize(0);
        assertThat(result.getValue())
                .extracting(HttpRequest::getHeaders)
                .flatExtracting(MultiMap::entries)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()),
                        tuple(HttpUtil.X_FORWARDED_FOR_HEADER.toString(), "ipv6"),
                        tuple(HttpUtil.X_OPENRTB_VERSION_HEADER.toString(), "2.5")
                );
    }

    @Test
    public void makeBidsShouldReturnValidBidResponseWithBanner() throws JsonProcessingException {
        //given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(List.of(givenImpressionWithValidBannerData()))
                .build();

        final HttpCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponseWithBidWithValidImpId()));

        //when
        final Result<List<BidderBid>> result = impactifyBidder.makeBids(httpCall, bidRequest);

        //then
        assertThat(result.getErrors()).hasSize(0);
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .containsOnly(Bid.builder()
                        .impid("123")
                        .build());

        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.banner);
    }

    @Test
    public void makeBidsWithInvalidBodyShouldResultInError() {
        //given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        //when
        final Result<List<BidderBid>> result = impactifyBidder.makeBids(httpCall, null);

        //then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Failed to decode: Unrecognized token");
    }

    @Test
    public void makeBidsReturnEmptyListsResultWhenEmptySeatBidInBidResponse() throws JsonProcessingException {
        //given
        final BidRequest bidRequest = BidRequest.builder()
                .build();

        final HttpCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponseWithEmptySeatBid()));

        //when
        final Result<List<BidderBid>> result = impactifyBidder.makeBids(httpCall, bidRequest);

        //then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnValidBidResponseWithVideo() throws JsonProcessingException {
        //given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(List.of(givenImpressionWithValidVideoData()))
                .build();

        final HttpCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponseWithBidWithValidImpId()));

        //when
        final Result<List<BidderBid>> result = impactifyBidder.makeBids(httpCall, bidRequest);

        //then
        assertThat(result.getErrors()).hasSize(0);
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .containsOnly(Bid.builder()
                        .impid("123")
                        .build());

        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.video);
    }

    @Test
    public void makeBidsShouldReturnErrorWithNoValidBidType() throws JsonProcessingException {
        //given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(List.of(givenImpressionWithNoValidBidType()))
                .build();

        final HttpCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponseWithBidWithValidImpId()));

        //when
        final Result<List<BidderBid>> result = impactifyBidder.makeBids(httpCall, bidRequest);

        //then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors())
                .extracting(BidderError::getMessage).containsExactly("Unknown impression type for ID: '123'");
    }

    @Test
    public void makeBidsShouldReturnErrorWhenBidResponseImpIdIsNotSameAsBidRequestImpId()
            throws JsonProcessingException {
        //given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(List.of(givenImpressionWithValidBannerData()))
                .build();

        final HttpCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponseWithBidWithInvalidImpId()));

        //when
        final Result<List<BidderBid>> result = impactifyBidder.makeBids(httpCall, bidRequest);

        //then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors())
                .extracting(BidderError::getMessage).containsExactly("Failed to find impression for ID: '321'");
    }
}
