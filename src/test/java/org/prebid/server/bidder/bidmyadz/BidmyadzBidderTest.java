package org.prebid.server.bidder.bidmyadz;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.bidmyadz.ExtImpBidmyadz;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

public class BidmyadzBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com/";

    private BidmyadzBidder bidmyadzBidder;

    @Before
    public void setUp() {
        bidmyadzBidder = new BidmyadzBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndPointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new BidmyadzBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorsIfBidRequestDoesNotHaveDevice() {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder.device(null),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidmyadzBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors())
                .hasSize(2)
                .containsExactly(BidderError.badInput("IP/IPv6 is a required field"),
                        BidderError.badInput("User-Agent is a required field"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorsIfBidRequestDeviceDoesNotHaveIpOrIpV6() {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder.device(
                Device.builder().ua("ua").build()), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidmyadzBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).containsExactly(BidderError.badInput("IP/IPv6 is a required field"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorsIfBidRequestDeviceDoesNotHaveUserAgent() {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder.device(
                Device.builder().ip("ip").ipv6("ipv6").build()), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidmyadzBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).containsExactly(BidderError.badInput("User-Agent is a required field"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorsIfBidRequestHasMoreThanOneImp() {
        // given
        BidRequest bidRequestWithTwoImps = givenBidRequest(identity(), identity()).toBuilder()
                .imp(asList(Imp.builder().build(), Imp.builder().build())).build();
        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidmyadzBidder.makeHttpRequests(bidRequestWithTwoImps);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("Bidder does not support multi impression"));
    }

    @Test
    public void makeHttpRequestsShouldReturnBidRequestWithOpenRtbHeader() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidmyadzBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getHeaders)
                .flatExtracting(MultiMap::entries)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), "application/json;charset=utf-8"),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), "application/json"),
                        tuple(HttpUtil.X_OPENRTB_VERSION_HEADER.toString(), "2.5"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = bidmyadzBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors())
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfMediaTypeEqualsBanner() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123")
                                .ext(mapper.createObjectNode().put("mediaType", "banner")))));

        // when
        final Result<List<BidderBid>> result = bidmyadzBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).extracting(BidderBid::getType).containsExactly(BidType.banner);
    }

    @Test
    public void makeBidsShouldReturnErrorIfSeatBidIsEmpty() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").build()))
                        .build(),
                mapper.writeValueAsString(BidResponse.builder().seatbid(emptyList()).build()));

        // when
        final Result<List<BidderBid>> result = bidmyadzBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).containsExactly(BidderError.badServerResponse("Empty SeatBid"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfSeatBidDoesNotHaveBids() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").build()))
                        .build(),
                mapper.writeValueAsString(BidResponse.builder()
                        .seatbid(singletonList(SeatBid.builder().bid(emptyList()).build())).build()));

        // when
        final Result<List<BidderBid>> result = bidmyadzBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).containsExactly(BidderError.badServerResponse("Empty SeatBid.Bids"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfMediaTypeUnknown() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123")
                                .ext(mapper.createObjectNode().put("mediaType", "unknown")))));

        // when
        final Result<List<BidderBid>> result = bidmyadzBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).containsExactly(BidderError.badServerResponse(
                "No enum constant org.prebid.server.proto.openrtb.ext.response.BidType.unknown"
        ));
        assertThat(result.getValue()).isEmpty();
    }

    private static HttpCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return HttpCall.success(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body), null);
    }

    private static BidRequest givenBidRequest(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestCustomizer,
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                .device(Device.builder().ip("ip").ipv6("ipv6").ua("ua").build())
                .imp(singletonList(givenImp(impCustomizer))))
                .build();
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                .id("123")
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpBidmyadz.of("placementId")))))
                .build();
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
    }
}
