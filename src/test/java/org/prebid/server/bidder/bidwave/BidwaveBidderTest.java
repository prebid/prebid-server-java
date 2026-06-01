package org.prebid.server.bidder.bidwave;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
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
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.bidwave.ExtImpBidwave;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.BDDAssertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;

@ExtendWith(MockitoExtension.class)
public class BidwaveBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://rtb.bidwave.net/rtb/v1/bid";
    private static final String PUBLISHER_ID = "11111111-1111-4111-8111-111111111111";
    private static final String OTHER_PUBLISHER_ID = "22222222-2222-4222-8222-222222222222";

    @Mock
    private CurrencyConversionService currencyConversionService;

    private BidwaveBidder target;

    @BeforeEach
    public void setUp() {
        target = new BidwaveBidder(ENDPOINT_URL, currencyConversionService, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new BidwaveBidder("invalid_url", currencyConversionService, jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldSetBidwaveExtDefaultCurrencyAndPreserveDeviceGeo()
            throws IOException {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.id("imp-1").banner(givenBanner()).ext(givenImpExt(PUBLISHER_ID))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);

        final HttpRequest<BidRequest> httpRequest = result.getValue().getFirst();
        final BidRequest payload = httpRequest.getPayload();

        assertThat(httpRequest.getUri()).isEqualTo(ENDPOINT_URL);
        assertThat(httpRequest.getImpIds()).containsOnly("imp-1");
        assertThat(mapper.readValue(httpRequest.getBody(), BidRequest.class)).isEqualTo(payload);
        assertThat(payload.getCur()).containsExactly("USD");
        assertThat(payload.getExt().getProperty("bidwave").get("pid").asText()).isEqualTo(PUBLISHER_ID);
        assertThat(payload.getDevice()).isEqualTo(bidRequest.getDevice());
    }

    @Test
    public void makeHttpRequestsShouldGroupImpsByPublisherIdAndNormalizeCurrency() {
        // given
        final Imp firstImp = givenImp(imp -> imp.id("imp-1").banner(givenBanner()).ext(givenImpExt(PUBLISHER_ID)));
        final Imp secondImp = givenImp(imp -> imp.id("imp-2").video(givenVideo()).ext(givenImpExt(OTHER_PUBLISHER_ID)));
        final Imp thirdImp = givenImp(imp -> imp.id("imp-3").banner(givenBanner()).ext(givenImpExt(PUBLISHER_ID)));
        final BidRequest bidRequest = givenBidRequest(firstImp, secondImp, thirdImp)
                .toBuilder()
                .cur(List.of("EUR"))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2);
        assertThat(result.getValue().get(0).getPayload().getImp()).containsExactly(firstImp, thirdImp);
        assertThat(result.getValue().get(0).getPayload().getExt().getProperty("bidwave").get("pid").asText())
                .isEqualTo(PUBLISHER_ID);
        assertThat(result.getValue().get(0).getPayload().getCur()).containsExactly("USD");
        assertThat(result.getValue().get(1).getPayload().getImp()).containsExactly(secondImp);
        assertThat(result.getValue().get(1).getPayload().getExt().getProperty("bidwave").get("pid").asText())
                .isEqualTo(OTHER_PUBLISHER_ID);
        assertThat(result.getValue().get(1).getPayload().getCur()).containsExactly("USD");
    }

    @Test
    public void makeHttpRequestsShouldConvertBidFloorCurrency() {
        // given
        given(currencyConversionService.convertCurrency(any(), any(), anyString(), anyString()))
                .willReturn(BigDecimal.TEN);

        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp
                        .id("imp-1")
                        .banner(givenBanner())
                        .bidfloor(BigDecimal.ONE)
                        .bidfloorcur("EUR")
                        .ext(givenImpExt(PUBLISHER_ID))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor, Imp::getBidfloorcur)
                .containsExactly(tuple(BigDecimal.TEN, "USD"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenBidFloorCurrencyCannotBeConverted() {
        // given
        given(currencyConversionService.convertCurrency(any(), any(), anyString(), anyString()))
                .willThrow(new PreBidException("missing conversion rate"));

        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp
                        .id("imp-1")
                        .banner(givenBanner())
                        .bidfloor(BigDecimal.ONE)
                        .bidfloorcur("EUR")
                        .ext(givenImpExt(PUBLISHER_ID))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(BidderError.badInput(
                "expected currency USD for bid floor; unable to convert from EUR for imp `imp-1`"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorForInvalidPublisherId() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.id("imp-1").banner(givenBanner()).ext(givenImpExt("publisher-1"))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(
                BidderError.badInput("Invalid publisherId for impression imp-1"));
    }

    @Test
    public void makeBidsShouldReturnBannerAndVideoBids() throws JsonProcessingException {
        // given
        final Bid bannerBid = Bid.builder().impid("imp-1").mtype(1).build();
        final Bid videoBid = Bid.builder().impid("imp-2").mtype(2).build();
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.id("imp-1").banner(givenBanner()).ext(givenImpExt(PUBLISHER_ID))),
                givenImp(imp -> imp.id("imp-2").video(givenVideo()).ext(givenImpExt(PUBLISHER_ID))));
        final BidderCall<BidRequest> httpCall = sampleHttpCall(
                bidRequest,
                mapper.writeValueAsString(givenBidResponse(bannerBid, videoBid)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(
                BidderBid.of(bannerBid, banner, "USD"),
                BidderBid.of(videoBid, video, "USD"));
    }

    @Test
    public void makeBidsShouldInferBidTypeFromImpWhenMtypeIsAbsent() throws JsonProcessingException {
        // given
        final Bid bid = Bid.builder().impid("imp-1").build();
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.id("imp-1").video(givenVideo()).ext(givenImpExt(PUBLISHER_ID))));
        final BidderCall<BidRequest> httpCall = sampleHttpCall(
                bidRequest,
                mapper.writeValueAsString(givenBidResponse(bid)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(bid, video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenMtypeIsAbsentForMultiFormatImp() throws JsonProcessingException {
        // given
        final Bid bid = Bid.builder().impid("imp-1").build();
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp
                        .id("imp-1")
                        .banner(givenBanner())
                        .video(givenVideo())
                        .ext(givenImpExt(PUBLISHER_ID))));
        final BidderCall<BidRequest> httpCall = sampleHttpCall(
                bidRequest,
                mapper.writeValueAsString(givenBidResponse(bid)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(
                BidderError.badServerResponse(
                        "Bid must have non-null mtype for multi format impression with ID: \"imp-1\""));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenMtypeIsAbsentForUnknownImp() throws JsonProcessingException {
        // given
        final Bid bid = Bid.builder().impid("imp-2").build();
        final BidderCall<BidRequest> httpCall = sampleHttpCall(
                givenBidRequest(givenImp(imp -> imp.id("imp-1").banner(givenBanner()).ext(givenImpExt(PUBLISHER_ID)))),
                mapper.writeValueAsString(givenBidResponse(bid)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(
                BidderError.badServerResponse("Failed to find impression for ID: \"imp-2\""));
    }

    @Test
    public void makeBidsShouldReturnErrorIfMtypeIsUnsupported() throws JsonProcessingException {
        // given
        final Bid bid = Bid.builder().impid("imp-1").mtype(3).build();
        final BidderCall<BidRequest> httpCall = sampleHttpCall(
                givenBidRequest(givenImp(imp -> imp.id("imp-1").banner(givenBanner()).ext(givenImpExt(PUBLISHER_ID)))),
                mapper.writeValueAsString(givenBidResponse(bid)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(
                BidderError.badServerResponse("Unsupported mtype 3 for imp imp-1"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = sampleHttpCall(givenBidRequest(), "invalid response");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allMatch(error -> error.getType() == BidderError.Type.bad_server_response
                        && error.getMessage().startsWith("Failed to decode: Unrecognized token"));
    }

    private static BidRequest givenBidRequest(Imp... imps) {
        return BidRequest.builder()
                .id("request-id")
                .site(Site.builder().domain("publisher.example.com").build())
                .device(Device.builder()
                        .ip("203.0.113.42")
                        .geo(Geo.builder().country("USA").region("CA").city("San Francisco").build())
                        .build())
                .imp(List.of(imps))
                .build();
    }

    private static BidRequest givenBidRequest() {
        return givenBidRequest(givenImp(imp -> imp.id("imp-1").banner(givenBanner()).ext(givenImpExt(PUBLISHER_ID))));
    }

    private static Imp givenImp(java.util.function.Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()).build();
    }

    private static Banner givenBanner() {
        return Banner.builder()
                .format(singletonList(Format.builder().w(300).h(250).build()))
                .build();
    }

    private static Video givenVideo() {
        return Video.builder()
                .mimes(singletonList("video/mp4"))
                .protocols(List.of(2, 3))
                .w(640)
                .h(360)
                .build();
    }

    private static com.fasterxml.jackson.databind.node.ObjectNode givenImpExt(String publisherId) {
        return mapper.valueToTree(ExtPrebid.of(null, ExtImpBidwave.of(publisherId)));
    }

    private static BidResponse givenBidResponse(Bid... bids) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder().bid(List.of(bids)).build()))
                .build();
    }

    private static BidderCall<BidRequest> sampleHttpCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
