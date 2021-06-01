package org.prebid.server.bidder.triplelift;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.triplelift.model.TripleliftInnerExt;
import org.prebid.server.bidder.triplelift.model.TripleliftResponseExt;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.triplelift.ExtImpTriplelift;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;

public class TripleliftBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";

    private TripleliftBidder tripleliftBidder;

    @Before
    public void setUp() {
        tripleliftBidder = new TripleliftBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TripleliftBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void creationShouldFailWhenNotBannerOrVideoIsPresent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().build()))
                .id("request_id")
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = tripleliftBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(2);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("neither Banner nor Video object specified");
        assertThat(result.getErrors().get(1).getMessage()).startsWith("No valid impressions for triplelift");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnTwoErrorsIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))
                        .banner(Banner.builder().build())
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = tripleliftBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(2);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Cannot deserialize instance");
        assertThat(result.getErrors().get(1).getMessage()).startsWith("No valid impressions for triplelift");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldModifyTagIdFromImpExt() {
        // given
        final String inventoryCode = "inventoryCode";
        final ObjectNode ext = mapper.valueToTree(ExtPrebid.of(null, ExtImpTriplelift.of(inventoryCode, null)));
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .tagid("tag")
                        .banner(Banner.builder().build())
                        .ext(ext)
                        .build()))
                .id("request_id")
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = tripleliftBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getTagid)
                .containsOnly(inventoryCode);
    }

    @Test
    public void makeHttpRequestsShouldModifyBidFloorFromImpExtWhenFloorIsPresent() {
        // given
        final BigDecimal floor = BigDecimal.valueOf(12.32);
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .bidfloor(new BigDecimal(1))
                        .banner(Banner.builder().build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpTriplelift.of(null, floor))))
                        .build()))
                .id("request_id")
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = tripleliftBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor)
                .containsOnly(floor);
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = tripleliftBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Failed to decode: Unrecognized token");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = tripleliftBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                null,
                mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = tripleliftBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorWhenBidExtIsEmpty() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                null,
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = tripleliftBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Empty ext in bid ");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnTypeBannerWhenTripleliftResponseExtIsEmpty() throws JsonProcessingException {
        // given
        final ObjectNode ext = mapper.valueToTree(TripleliftResponseExt.of(null));
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                null,
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.ext(ext))));

        // when
        final Result<List<BidderBid>> result = tripleliftBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().ext(ext).build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnTypeBannerWhenTripleliftInnerExtIsNull() throws JsonProcessingException {
        // given
        final ObjectNode ext = mapper.valueToTree(TripleliftResponseExt.of(TripleliftInnerExt.of(null)));
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                null,
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.ext(ext))));

        // when
        final Result<List<BidderBid>> result = tripleliftBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().ext(ext).build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnTypeBannerWhenTripleliftInnerExtIsNotEleven() throws JsonProcessingException {
        // given
        final ObjectNode ext = mapper.valueToTree(TripleliftResponseExt.of(TripleliftInnerExt.of(0)));
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                null,
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.ext(ext))));

        // when
        final Result<List<BidderBid>> result = tripleliftBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().ext(ext).build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnTypeVideoWhenTripleliftInnerExtIsEleven() throws JsonProcessingException {
        // given
        final ObjectNode ext = mapper.valueToTree(TripleliftResponseExt.of(TripleliftInnerExt.of(11)));
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                null,
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.ext(ext))));

        // when
        final Result<List<BidderBid>> result = tripleliftBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().ext(ext).build(), video, "USD"));
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
    }

    private static HttpCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return HttpCall.success(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
