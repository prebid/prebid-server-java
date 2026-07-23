package org.prebid.server.bidder.synapsehx;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.MultiMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.synapsehx.ExtImpSynapseHX;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

public class SynapseHXBidderTest extends VertxTest {

    private SynapseHXBidder target;

    @BeforeEach
    public void setUp() {
        target = new SynapseHXBidder("http://test.endpoint.com", jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new SynapseHXBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldCorrectlyAddHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getHeaders)
                .flatExtracting(MultiMap::entries)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()),
                        tuple(HttpUtil.X_OPENRTB_VERSION_HEADER.toString(), "2.6"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpressionContainsInvalidBidderParameters() {
        // given
        final ObjectNode invalidExt = mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()));

        final BidRequest bidRequest = givenBidRequest(identity(),
                impBuilder -> impBuilder.id("Imp01").ext(invalidExt));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).satisfiesExactly(
                error -> assertThat(error.getMessage()).startsWith("Failed to parse bidder parameters"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenRequestHasNoImps() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.imp(new ArrayList<>()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).satisfiesExactly(
                error -> assertThat(error.getMessage()).isEqualTo("Request has no imps"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

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
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldSkipBidWithUnsupportedBidTypeAndKeepValidBid() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null,
                givenBidResponse(
                        givenBidWithMType("Imp1", BidType.banner),
                        givenBidWithMType("Imp2", BidType.audio)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).satisfiesExactly(
                error -> assertThat(error.getMessage()).startsWith("Unsupported media type"));
        assertThat(result.getValue()).satisfiesExactly(
                bid -> assertThat(bid.getBid().getImpid()).isEqualTo("Imp1"));
    }

    @Test
    public void makeBidsShouldReportErrorsSkipForBidsWithUnsupportedOrMissingBidType() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null,
                givenBidResponse(
                        givenBidWithMType("Imp1", BidType.xNative),
                        givenBidWithMType("Imp2", BidType.audio),
                        givenBid("Imp3", BidType.xNative),
                        givenBid("Imp4", BidType.audio),
                        givenBid("Imp9", BidType.banner).toBuilder().ext(null).build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(5).allSatisfy(
                error -> assertThat(error.getMessage()).startsWith("Unsupported media type"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsCorrectlyRecogniseBidTypeWithFallback() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null,
                givenBidResponse(
                        givenBidWithMType("Imp1", BidType.banner),
                        givenBidWithMType("Imp2", BidType.video),
                        givenBid("Imp3", BidType.banner),
                        givenBid("Imp4", BidType.video)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).satisfiesExactlyInAnyOrder(
                bid -> {
                    assertThat(bid.getBid().getImpid()).isEqualTo("Imp1");
                    assertThat(bid.getType()).isEqualTo(BidType.banner);
                },
                bid -> {
                    assertThat(bid.getBid().getImpid()).isEqualTo("Imp2");
                    assertThat(bid.getType()).isEqualTo(BidType.video);
                },
                bid -> {
                    assertThat(bid.getBid().getImpid()).isEqualTo("Imp3");
                    assertThat(bid.getType()).isEqualTo(BidType.banner);
                },
                bid -> {
                    assertThat(bid.getBid().getImpid()).isEqualTo("Imp4");
                    assertThat(bid.getType()).isEqualTo(BidType.video);
                }
        );
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer);
    }

    private static BidRequest givenBidRequest(
            UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
            UnaryOperator<Imp.ImpBuilder> impCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                        .imp(singletonList(givenImp(impCustomizer))))
                .build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return givenImp(impCustomizer, ExtImpSynapseHX.of("tenant-id"));
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer, ExtImpSynapseHX extImp) {
        return impCustomizer.apply(Imp.builder()
                        .id("345")
                        .banner(Banner.builder().build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, extImp))))
                .build();
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }

    private BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, BidResponse bidResponse)
            throws JsonProcessingException {
        return givenHttpCall(bidRequest, mapper.writeValueAsString(bidResponse));
    }

    private static BidResponse givenBidResponse(Bid... bids) {
        return BidResponse.builder().seatbid(singletonList(
                SeatBid.builder().bid(asList(bids)).build())).build();
    }

    private static Bid givenBid(String impid, BidType bidType) {
        return Bid.builder()
                .impid(impid)
                .ext(mapper.createObjectNode()
                        .set("prebid", mapper.createObjectNode()
                                .put("type", bidType.getName())))
                .build();
    }

    private static Bid givenBidWithMType(String impid, BidType bidType) {
        return Bid.builder().impid(impid).mtype(bidType.ordinal() + 1).build();
    }

}
