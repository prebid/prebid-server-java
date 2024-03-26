package org.prebid.server.bidder.zmaticoo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.zmaticoo.ExtImpZMaticoo;

import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

public class ZMaticooBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.com/test";

    private final ZMaticooBidder target = new ZMaticooBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ZMaticooBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorsOnInvalidImps() {
        // given
        final ObjectNode invalidNode = mapper.createObjectNode();
        invalidNode.putArray("bidder");
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.ext(invalidNode)),
                givenImp(ExtImpZMaticoo.of("", "")),
                givenImp(ExtImpZMaticoo.of("pubId", "")),
                givenImp(ExtImpZMaticoo.of("", "zoneId")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).satisfies(errors -> {
            assertThat(errors.get(0)).satisfies(error -> {
                assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                assertThat(error.getMessage()).startsWith("Cannot deserialize value of type");
            });
            assertThat(errors.stream().skip(1))
                    .map(BidderError::getType, BidderError::getMessage)
                    .containsOnly(tuple(BidderError.Type.bad_input, "imp.ext.pubId or imp.ext.zoneId required"));
        });
    }

    @Test
    public void makeHttpRequestsShouldProperlyHandleValidNative() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(identity()),
                givenImp(imp -> imp.xNative(Native.builder().build())),
                givenImp(imp -> imp.xNative(Native.builder()
                        .request("""
                                {
                                    "native": 1
                                }
                                """)
                        .build())),
                givenImp(imp -> imp.xNative(Native.builder()
                        .request("""
                                {
                                    "field": 0
                                }
                                """)
                        .build())));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getImp)
                .allSatisfy(imps -> {
                    assertThat(imps.get(0)).isSameAs(bidRequest.getImp().get(0));
                    assertThat(imps.get(1))
                            .extracting(Imp::getXNative)
                            .extracting(Native::getRequest)
                            .isEqualTo("{\"native\":{}}");
                    assertThat(imps.get(2)).isSameAs(bidRequest.getImp().get(2));
                    assertThat(imps.get(3))
                            .extracting(Imp::getXNative)
                            .extracting(Native::getRequest)
                            .isEqualTo("{\"native\":{\"field\":0}}");
                });
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorOnInvalidNativeRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.xNative(Native.builder().request("invalid").build())));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1).allSatisfy(error -> {
            assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
            assertThat(error.getMessage()).startsWith("Unrecognized token 'invalid': was");
        });
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1).allSatisfy(error -> {
            assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
            assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token");
        });
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsEmpty() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse());

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnValidBids() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(
                        Bid.builder().id("1").mtype(1).build(),
                        Bid.builder().id("2").mtype(2).build(),
                        Bid.builder().id("3").impid("impId3").mtype(null).build(),
                        Bid.builder().id("4").mtype(4).build(),
                        Bid.builder().id("5").impid("impId5").mtype(0).build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue())
                .extracting(bidderBid -> bidderBid.getBid().getId(), BidderBid::getType)
                .containsExactly(tuple("1", banner), tuple("2", video), tuple("4", xNative));
        assertThat(result.getErrors()).containsExactly(
                BidderError.badServerResponse("unrecognized bid type in response from zmaticoo for bid impId3"),
                BidderError.badServerResponse("unrecognized bid type in response from zmaticoo for bid impId5"));
    }

    private static BidRequest givenBidRequest(Imp... imp) {
        return BidRequest.builder().imp(List.of(imp)).build();
    }

    private static Imp givenImp(ExtImpZMaticoo extImpZMaticoo) {
        return Imp.builder()
                .ext(mapper.valueToTree(ExtPrebid.of(null, extImpZMaticoo)))
                .build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer
                .apply(Imp.builder().ext(mapper.valueToTree(
                        ExtPrebid.of(null, ExtImpZMaticoo.of("pubId", "pubId")))))
                .build();
    }

    private static String givenBidResponse(Bid... bids) throws JsonProcessingException {
        return mapper.writeValueAsString(BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder().bid(List.of(bids)).build()))
                .build());
    }

    private static BidderCall<BidRequest> givenHttpCall(String body) {
        return BidderCall.succeededHttp(null, HttpResponse.of(200, null, body), null);
    }
}
