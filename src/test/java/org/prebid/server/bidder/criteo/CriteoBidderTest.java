package org.prebid.server.bidder.criteo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.CompositeBidderResponse;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.FledgeAuctionConfig;

import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.prebid.server.util.HttpUtil.ACCEPT_HEADER;
import static org.prebid.server.util.HttpUtil.APPLICATION_JSON_CONTENT_TYPE;
import static org.prebid.server.util.HttpUtil.CONTENT_TYPE_HEADER;
import static org.prebid.server.util.HttpUtil.headers;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;

public class CriteoBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";

    private final CriteoBidder target = new CriteoBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> new CriteoBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldEncodePassedBidRequest() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .id("id")
                .imp(List.of(Imp.builder().id("imp_id").build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final MultiMap expectedHeaders = headers()
                .set(CONTENT_TYPE_HEADER, APPLICATION_JSON_CONTENT_TYPE)
                .set(ACCEPT_HEADER, APPLICATION_JSON_VALUE);
        final Result<List<HttpRequest<BidRequest>>> expectedResult = Result.withValue(HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(ENDPOINT_URL)
                .headers(expectedHeaders)
                .impIds(Set.of("imp_id"))
                .body(jacksonMapper.encodeToBytes(bidRequest))
                .payload(bidRequest)
                .build());
        assertThat(result.getValue()).usingRecursiveComparison().isEqualTo(expectedResult.getValue());
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidderResponseShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("invalid");

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token");
                });
        assertThat(result.getBids()).isEmpty();
    }

    @Test
    public void makeBidderResponseShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(null));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids()).isEmpty();
    }

    @Test
    public void makeBidderResponseShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids()).isEmpty();
    }

    @Test
    public void makeBidderResponseShouldReturnEmptyListIfBidResponseSeatBidIsEmpty() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(BidResponse.builder().seatbid(emptyList()).build()));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids()).isEmpty();
    }

    @Test
    public void makeBidderResponseShouldReturnErrorWhenBidExtPrebidTypeIsNotPresent() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(bid -> bid.impid("123")));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badServerResponse("Missing ext.prebid.type in bid for impression : 123."));
        assertThat(result.getBids()).isEmpty();
    }

    @Test
    public void makeBidderResponseShouldReturnBannerBid() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(bid -> bid.ext(givenBidExt(BidType.banner))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.banner);
    }

    @Test
    public void makeBidderResponseShouldReturnVideoBid() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(bid -> bid.ext(givenBidExt(BidType.video))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.video);
    }

    @Test
    public void makeBidderResponseShouldReturnBidWithCurFromResponse() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(bid -> bid.ext(givenBidExt(BidType.banner))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids())
                .extracting(BidderBid::getBidCurrency)
                .containsExactly("CUR");
    }

    @Test
    public void makeBidderResponseShouldReturnBidWithNetworkNameFromExtPrebid() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(bid -> bid
                                .impid("123")
                        .ext(givenBidExtWithNetwork("anyNetworkName"))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getExt)
                .extracting(ext -> ext.get("meta"))
                .containsExactly(mapper.createObjectNode().put("networkName", "anyNetworkName"));
    }

    @Test
    public void makeBidderResponseShouldReturnEmptyNetworkNameWhenBidExtPrebidNotContainNetworkName()
            throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(bid -> bid.ext(givenBidExtWithNetwork(null))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getExt)
                .extracting(ext -> ext.get("meta"))
                .doesNotContain(mapper.createObjectNode().put("networkName", "anyNetworkName"));
    }

    @Test
    public void makeBidderResponseShouldReturnFledgeConfigs() throws JsonProcessingException {
        // given
        final CriteoBidResponse bidResponseWithFledge = CriteoBidResponse.builder()
                .ext(CriteoExtBidResponse.of(List.of(
                        CriteoIgiExtBidResponse.of("imp_id1", List.of(
                                CriteoIgsIgiExtBidResponse.of(
                                        mapper.createObjectNode().put("proterty1", "value1")),
                                CriteoIgsIgiExtBidResponse.of(
                                        mapper.createObjectNode().put("proterty2", "value2")))),
                        CriteoIgiExtBidResponse.of("imp_id2", List.of(
                                CriteoIgsIgiExtBidResponse.of(
                                        mapper.createObjectNode().put("proterty3", "value3")),
                                CriteoIgsIgiExtBidResponse.of(
                                        mapper.createObjectNode().put("proterty4", "value4")))))))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(bidResponseWithFledge));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getFledgeAuctionConfigs())
                .containsExactlyInAnyOrder(
                        FledgeAuctionConfig.builder()
                                .bidder("criteo")
                                .impId("imp_id1")
                                .config(mapper.createObjectNode().put("proterty1", "value1"))
                                .build(),
                        FledgeAuctionConfig.builder()
                                .bidder("criteo")
                                .impId("imp_id2")
                                .config(mapper.createObjectNode().put("proterty3", "value3"))
                                .build());
    }

    @Test
    public void makeBidsShouldFail() throws JsonProcessingException {
        //given
        final BidderCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).containsExactly(BidderError.generic("Deprecated adapter method invoked"));
    }

    private static String givenBidResponse(UnaryOperator<Bid.BidBuilder> bidCustomizer)
            throws JsonProcessingException {

        return mapper.writeValueAsString(BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .cur("CUR")
                .build());
    }

    private static BidderCall<BidRequest> givenHttpCall(String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(null).build(),
                HttpResponse.of(200, null, body),
                null);
    }

    private static ObjectNode givenBidExt(BidType bidType) {
        final ObjectNode ext = mapper.createObjectNode();
        ext.putObject("prebid").put("type", bidType.getName());
        return ext;
    }

    private static ObjectNode givenBidExtWithNetwork(String networkNameValue) {
        final ObjectNode ext = mapper.createObjectNode();
        final ObjectNode prebid = ext.putObject("prebid");
        prebid.put("type", BidType.banner.getName());
        prebid.put("networkName", networkNameValue);
        return ext;
    }

}
