package org.prebid.server.bidder.apacdex;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.MultiMap;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.apacdex.proto.ExtImpApacdex;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.util.HttpUtil;

import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.bidder.model.BidderError.Type.bad_server_response;
import static org.prebid.server.proto.openrtb.ext.response.BidType.audio;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

public class ApacdexBidderTest extends VertxTest {

    public static final String ENDPOINT_URL = "https://test.endpoint.com";

    private ApacdexBidder apacdexBidder;

    @Before
    public void setUp() {
        apacdexBidder = new ApacdexBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ApacdexBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldCorrectlyAddHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = apacdexBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getHeaders)
                .flatExtracting(MultiMap::entries)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()),
                        tuple(HttpUtil.X_OPENRTB_VERSION_HEADER.toString(), "2.5"));
    }

    @Test
    public void makeHttpRequestsShouldReturnCorrectModifyImp() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = apacdexBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(correctExtTypeJsonNode());
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = apacdexBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getMessage()).startsWith("Cannot deserialize value of type");
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                });
    }

    @Test
    public void makeHttpRequestsShouldReturnOneRequestForAllImp() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(givenImp(identity()), givenImp(identity())))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = apacdexBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .hasSize(2);
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = apacdexBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token ");
                    assertThat(error.getType()).isEqualTo(bad_server_response);
                });
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall =
                givenHttpCall(null, mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = apacdexBidder.makeBids(httpCall, null);

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
        final Result<List<BidderBid>> result = apacdexBidder.makeBids(httpCall, null);

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
        final Result<List<BidderBid>> result = apacdexBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactlyInAnyOrder(
                        BidderBid.of(givenBid("123", banner), banner, null),
                        BidderBid.of(givenBid("456", video), video, null),
                        BidderBid.of(givenBid("789", audio), audio, null),
                        BidderBid.of(givenBid("44454", xNative), xNative, null));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenExtBidTypeNotFound() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(identity()),
                mapper.writeValueAsString(BidResponse.builder()
                        .seatbid(List.of(SeatBid.builder()
                                .bid(List.of(givenBid("124", null, bidBuilder -> bidBuilder.ext(null))))
                                .build()))
                        .build()));

        // when
        final Result<List<BidderBid>> result = apacdexBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).hasSize(0);
        assertThat(result.getErrors()).containsExactly(
                BidderError.badServerResponse("Failed to parse bid media type for impression 124"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenUnrecognisedTypeAndOneValue() throws JsonProcessingException {
        // given
        final ObjectNode givenExt = getExtUnknownTypeJsonNode();

        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(identity()),
                mapper.writeValueAsString(BidResponse.builder()
                        .seatbid(List.of(SeatBid.builder()
                                .bid(List.of(
                                        givenBid("123", banner, identity()),
                                        givenBid("124", null, bidBuilder -> bidBuilder.ext(givenExt))))
                                .build()))
                        .build()));

        // when
        final Result<List<BidderBid>> result = apacdexBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getErrors()).containsExactly(BidderError.badServerResponse("invalid BidType: unknownType"));
    }

    private static BidRequest givenBidRequest(
            UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
            UnaryOperator<Imp.ImpBuilder> impCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                        .imp(singletonList(givenImp(impCustomizer))))
                .build();
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer);
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("123")
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpApacdex.of("123", "345", 4.4f)))))
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
                null);
    }

    private static Bid givenBid(String impid, BidType bidType, UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return bidCustomizer.apply(Bid.builder()
                        .impid(impid)
                        .ext(mapper.valueToTree(ExtPrebid.of(ExtBidPrebid.builder().type(bidType).build(), null))))
                .build();
    }

    private static Bid givenBid(String impid, BidType bidType) {
        return givenBid(impid, bidType, UnaryOperator.identity());
    }

    private static List<SeatBid> givenSeatBid(Bid... bids) {
        return singletonList(SeatBid.builder().bid(asList(bids)).build());
    }

    private static ObjectNode getExtUnknownTypeJsonNode() {
        final ObjectNode givenExtPrebid = mapper.createObjectNode().put("type", "unknownType");
        return mapper.createObjectNode().set("prebid", givenExtPrebid);
    }

    private static ObjectNode correctExtTypeJsonNode() {
        final ExtImpApacdex extImpApacdex = ExtImpApacdex.of("123", "345", 4.4f);
        return mapper.valueToTree(extImpApacdex);
    }
}
