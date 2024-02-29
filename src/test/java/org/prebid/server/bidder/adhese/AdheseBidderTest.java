package org.prebid.server.bidder.adhese;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.adhese.model.AdheseOriginData;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.adhese.ExtImpAdhese;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class AdheseBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://ads-{{AccountId}}.adhese.com/openrtb2";

    private final AdheseBidder target = new AdheseBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AdheseBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpressionListSizeIsZero() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(emptyList())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("No impression in the bid request"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Cannot deserialize value");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldAdjustExtAndAdjustUri() {
        // given
        final Map<String, List<String>> targets = Map.of(
                "ci", asList("gent", "brussels"),
                "ag", singletonList("55"),
                "tl", singletonList("all"));
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("impId")
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpAdhese.of(
                                "demo",
                                "_adhese_prebid_demo_",
                                "leaderboard",
                                mapper.convertValue(targets, JsonNode.class)))))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        final TreeMap<String, List<String>> expectedParameters = new TreeMap<>();
        expectedParameters.put("ag", singletonList("55"));
        expectedParameters.put("ci", List.of("gent", "brussels"));
        expectedParameters.put("tl", singletonList("all"));
        expectedParameters.put("SL", singletonList("_adhese_prebid_demo_-leaderboard"));

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).satisfiesOnlyOnce(httpRequest -> {
            assertThat(httpRequest.getMethod()).isEqualTo(HttpMethod.POST);
            assertThat(httpRequest.getUri()).isEqualTo("https://ads-demo.adhese.com/openrtb2");
        });
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getId)
                .containsExactly("impId");
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .extracting(jsonNode -> jsonNode.get("adhese"))
                .containsExactly(mapper.valueToTree(expectedParameters));
    }

    @Test
    public void makeBidsShouldReturnErrorIfBidResponseInvalid() {
        // given
        final BidderCall<BidRequest> response = givenHttpCall("invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(response, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).satisfiesOnlyOnce(error -> {
            assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
            assertThat(error.getMessage()).startsWith("Failed to decode");
        });
    }

    @Test
    public void makeBidsShouldReturnEmptyResultIfSeatBidNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> response = givenHttpCall(BidResponse.builder().build());

        // when
        final Result<List<BidderBid>> result = target.makeBids(response, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyResultIfSeatBidEmpty() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> response = givenHttpCall(BidResponse.builder().seatbid(emptyList()).build());

        // when
        final Result<List<BidderBid>> result = target.makeBids(response, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyResultIfBidNull() throws JsonProcessingException {
        // given
        final BidResponse bidResponse = BidResponse.builder()
                .seatbid(List.of(SeatBid.builder().build()))
                .build();
        final BidderCall<BidRequest> response = givenHttpCall(bidResponse);

        // when
        final Result<List<BidderBid>> result = target.makeBids(response, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyResultIfBidEmpty() throws JsonProcessingException {
        // given
        final BidResponse bidResponse = BidResponse.builder()
                .seatbid(List.of(SeatBid.builder().bid(emptyList()).build()))
                .build();
        final BidderCall<BidRequest> response = givenHttpCall(bidResponse);

        // when
        final Result<List<BidderBid>> result = target.makeBids(response, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfBidTypeNotFound() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("impId")
                        .build()))
                .build();
        final AdheseOriginData adheseOriginData = AdheseOriginData.of("priority", "orderProperty", "adFormat",
                "adType", "adspaceId", "libId", "slotID", "viewableImpressionCounter");
        final ObjectNode adheseExtNode = mapper.createObjectNode().set("adhese", mapper.valueToTree(adheseOriginData));
        final Bid bid = Bid.builder()
                .id("bidId")
                .impid("impId")
                .price(BigDecimal.valueOf(1))
                .crid("60613369")
                .dealid("888")
                .w(728)
                .h(90)
                .ext(adheseExtNode)
                .build();
        final BidResponse bidResponse = BidResponse.builder()
                .seatbid(List.of(SeatBid.builder().bid(singletonList(bid)).build()))
                .cur("USD")
                .build();
        final BidderCall<BidRequest> response = givenHttpCall(bidResponse);

        // when
        final Result<List<BidderBid>> result = target.makeBids(response, bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).satisfiesOnlyOnce(error -> {
            assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
            assertThat(error.getMessage()).startsWith("Failed to obtain BidType");
        });
    }

    @Test
    public void makeBidsShouldReturnCorrectBid() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("impId")
                        .banner(Banner.builder().build())
                        .build()))
                .build();
        final AdheseOriginData adheseOriginData = AdheseOriginData.of("priority", "orderProperty", "adFormat",
                "adType", "adspaceId", "libId", "slotID", "viewableImpressionCounter");
        final JsonNode adheseOriginDataNode = mapper.valueToTree(adheseOriginData);
        final ObjectNode adheseExtNode = mapper.createObjectNode().set("adhese", adheseOriginDataNode);
        final Bid bid = Bid.builder()
                .id("bidId")
                .impid("impId")
                .price(BigDecimal.valueOf(1))
                .crid("60613369")
                .dealid("888")
                .w(728)
                .h(90)
                .ext(adheseExtNode)
                .build();
        final BidResponse bidResponse = BidResponse.builder()
                .seatbid(List.of(SeatBid.builder().bid(singletonList(bid)).build()))
                .cur("USD")
                .build();
        final BidderCall<BidRequest> response = givenHttpCall(bidResponse);

        // when
        final Result<List<BidderBid>> result = target.makeBids(response, bidRequest);

        // then
        final Bid expectedBid = bid.toBuilder()
                .ext(mapper.valueToTree(adheseOriginDataNode))
                .build();
        final BidderBid expected = BidderBid.of(expectedBid, BidType.banner, "USD");

        assertThat(result.getValue()).doesNotContainNull().hasSize(1).first().isEqualTo(expected);
        assertThat(result.getErrors()).isEmpty();
    }

    private static BidderCall<BidRequest> givenHttpCall(String responseBody) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().build(),
                HttpResponse.of(200, null, responseBody), null);
    }

    private static BidderCall<BidRequest> givenHttpCall(BidResponse response) throws JsonProcessingException {
        return givenHttpCall(mapper.writeValueAsString(response));
    }
}
