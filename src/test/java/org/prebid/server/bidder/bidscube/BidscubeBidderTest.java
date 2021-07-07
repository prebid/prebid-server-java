package org.prebid.server.bidder.bidscube;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpHeaderValues;
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
import org.prebid.server.proto.openrtb.ext.request.bidscube.ExtImpBidscube;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.util.HttpUtil;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.proto.openrtb.ext.response.BidType.audio;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

public class BidscubeBidderTest extends VertxTest {

    private BidscubeBidder bidscubeBidder;

    @Before
    public void setUp() {
        bidscubeBidder = new BidscubeBidder(jacksonMapper, "https://randomurl.com");
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new BidscubeBidder(jacksonMapper, "invalid_url"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtContainNullPlacementIdParam() {
        // given
        final Imp imp = givenImp(impBuilder -> impBuilder
                .id("456")
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpBidscube.of(null)))));

        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(imp))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidscubeBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(
                BidderError.badInput("Missing required bidder parameters"));
    }

    @Test
    public void makeHttpRequestsShouldCorrectlyAddHeaders() {
        // given
        final Imp firstImp = givenImp(impBuilder -> impBuilder
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpBidscube.of("someId")))));

        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(firstImp))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidscubeBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .flatExtracting(res -> res.getHeaders().entries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorsOfNotValidImps() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidscubeBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("Missing required bidder parameters"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = bidscubeBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = bidscubeBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = bidscubeBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnValidBidderBids() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidResponse.builder()
                        .seatbid(givenSeatBid(
                                givenBid("123", banner),
                                givenBid("456", video),
                                givenBid("789", audio),
                                givenBid("44454", xNative)))
                        .build());

        // when
        final Result<List<BidderBid>> result = bidscubeBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactlyInAnyOrder(
                        BidderBid.of(givenBid("123", banner), banner, null),
                        BidderBid.of(givenBid("456", video), video, null),
                        BidderBid.of(givenBid("789", audio), banner, null),
                        BidderBid.of(givenBid("44454", xNative), xNative, null));
    }

    @Test
    public void makeBidsShouldReturnBannerBidForUnrecognisedType() throws JsonProcessingException {
        // given
        final ObjectNode givenExtPrebid = mapper.createObjectNode().put("type", "unknownType");
        final ObjectNode givenExt = mapper.createObjectNode().set("prebid", givenExtPrebid);

        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidResponse.builder()
                        .seatbid(givenSeatBid(
                                givenBid("123", null, bidBuilder -> bidBuilder.ext(givenExt)))
                        ).build());

        // when
        final Result<List<BidderBid>> result = bidscubeBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(givenBid("123", video, bidBuilder -> bidBuilder.ext(givenExt)),
                        banner, null));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorOnEmptyBidExt() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidResponse.builder()
                        .seatbid(givenSeatBid(
                                givenBid("123", banner, bidBuilder -> bidBuilder.ext(null)),
                                givenBid("456", banner, bidBuilder -> bidBuilder.ext(mapper.valueToTree(
                                        ExtPrebid.of(null, null)))),
                                givenBid("213", null)
                                )
                        ).build());

        // when
        final Result<List<BidderBid>> result = bidscubeBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(
                BidderError.badInput("Unable to read bid.ext.prebid.type"),
                BidderError.badInput("Unable to read bid.ext.prebid.type"),
                BidderError.badInput("Unable to read bid.ext.prebid.type"));
    }

    @Test
    public void makeBidsShouldReturnErrorOnInvalidBidExt() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(BidResponse.builder()
                .seatbid(givenSeatBid(givenBid("123", banner, bidBuilder ->
                        bidBuilder.ext(mapper.valueToTree(ExtPrebid.of("invalid", null)))))).build());

        // when
        final Result<List<BidderBid>> result = bidscubeBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(
                BidderError.badInput("Unable to read bid.ext.prebid.type"));
    }

    private static BidRequest givenBidRequest(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestCustomizer,
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                .imp(singletonList(givenImp(impCustomizer))))
                .build();
    }

    private static BidRequest givenBidRequest(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer);
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                .id("123")
                .banner(Banner.builder().w(23).h(25).build())
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpBidscube.of("someId")))))
                .build();
    }

    private static HttpCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return HttpCall.success(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }

    private static HttpCall<BidRequest> givenHttpCall(BidResponse bidResponse) throws JsonProcessingException {
        return HttpCall.success(
                null,
                HttpResponse.of(200, null, mapper.writeValueAsString(bidResponse)),
                null
        );
    }

    private static Bid givenBid(String impid, BidType bidType, Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return bidCustomizer.apply(Bid.builder()
                .impid(impid)
                .ext(mapper.valueToTree(ExtPrebid.of(ExtBidPrebid.builder().type(bidType).build(), null)))
        ).build();
    }

    private static Bid givenBid(String impid, BidType bidType) {
        return givenBid(impid, bidType, identity());
    }

    private static List<SeatBid> givenSeatBid(Bid... bids) {
        return singletonList(SeatBid.builder().bid(asList(bids)).build());
    }
}
