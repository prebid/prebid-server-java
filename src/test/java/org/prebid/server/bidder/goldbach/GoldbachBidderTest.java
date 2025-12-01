package org.prebid.server.bidder.goldbach;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.goldbach.ExtImpGoldbach;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

public class GoldbachBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com/";

    private final GoldbachBidder target = new GoldbachBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new GoldbachBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedRequestUrl() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly(ENDPOINT_URL);
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedRequestMethod() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getMethod)
                .containsExactly(HttpMethod.POST);
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedRequestHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getHeaders)
                .flatExtracting(MultiMap::entries)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                        tuple(
                                HttpUtil.ACCEPT_HEADER.toString(),
                                HttpHeaderValues.APPLICATION_JSON.toString()));
    }

    @Test
    public void makeHttpRequestsShouldExtendBidRequestIdWithPublisherId() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getId)
                .containsExactly("testBidRequestId_testPublisherId");
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfGoldbachBidRequestExtensionIsInvalid() {
        // given
        final ExtRequest extRequest = ExtRequest.empty();
        extRequest.addProperty("goldbach", TextNode.valueOf("Invalid request.ext"));
        final BidRequest bidRequest = givenBidRequest(request -> request.ext(extRequest), givenImp());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getErrors()).hasSize(1).allSatisfy(error -> {
            assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
            assertThat(error.getMessage()).startsWith("Failed to deserialize Goldbach bid request extension: ");
        });
    }

    @Test
    public void makeHttpRequestsShouldAddPublisherIdToGoldbachBidRequestExtension() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> httpRequest.getPayload().getExt().getProperties().get("goldbach"))
                .containsExactly(mapper.createObjectNode().put("publisherId", "testPublisherId"));
    }

    @Test
    public void makeHttpRequestsShouldPreserveMockResponseFieldInGoldbachBidRequestExtension() {
        // given
        final ExtRequest extRequest = ExtRequest.empty();
        extRequest.addProperty("goldbach", mapper.createObjectNode().put("mockResponse", true));
        final BidRequest bidRequest = givenBidRequest(request -> request.ext(extRequest), givenImp());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> httpRequest.getPayload().getExt().getProperties().get("goldbach"))
                .containsExactly(mapper.createObjectNode()
                        .put("publisherId", "testPublisherId")
                        .put("mockResponse", true));
    }

    @Test
    public void makeHttpRequestsShouldPreserveOtherBidRequestExtensions() {
        // given
        final ExtRequest extRequest = ExtRequest.empty();
        final JsonNode anotherExtension = TextNode.valueOf("anotherExtensionValue");
        extRequest.addProperty("anotherExtension", anotherExtension);
        final BidRequest bidRequest = givenBidRequest(builder -> builder.ext(extRequest), givenImp());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> httpRequest.getPayload().getExt().getProperties().get("anotherExtension"))
                .containsExactly(anotherExtension);
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtBidderIsInvalid() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(
                mapper.createObjectNode().put("bidder", "Invalid imp.ext.bidder")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(2).satisfiesExactlyInAnyOrder(
                error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).startsWith("Failed to deserialize Goldbach imp extension: ");
                },
                error ->
                        assertThat(error).isEqualTo(BidderError.badInput("No valid impressions found")));
    }

    @Test
    public void makeHttpRequestsShouldReplaceImpExt() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(mapper.createObjectNode().set(
                        "goldbach",
                        mapper.createObjectNode().put("slotId", "testSlotId")));
    }

    @Test
    public void makeHttpRequestsShouldCopyCustomTargetingToOutputImpExt() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(givenImpExt(
                "testPublisherId",
                "testSlotId",
                Map.of("key", List.of("value1", "value2")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final ObjectNode expectedImpExt = mapper.valueToTree(Map.of("goldbach", Map.of(
                "slotId", "testSlotId",
                "targetings", Map.of(
                        "key", List.of("value1", "value2")))));
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(expectedImpExt);
    }

    @Test
    public void makeHttpRequestsShouldParseSingleStringAsArrayInCustomTargeting() {
        // given
        final ObjectNode impExt = mapper.valueToTree(Map.of("bidder", Map.of(
                "publisherId", "testPublisherId",
                "slotId", "testSlotId",
                "customTargeting", Map.of(
                        "key1", "value1",
                        "key2", List.of("value2", "value3")))));
        final BidRequest bidRequest = givenBidRequest(givenImp(impExt));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final ObjectNode expectedImpExt = mapper.valueToTree(Map.of("goldbach", Map.of(
                "slotId", "testSlotId",
                "targetings", Map.of(
                        "key1", List.of("value1"),
                        "key2", List.of("value2", "value3")))));
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(expectedImpExt);
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfThereAreNoImpressions() {
        // given
        final BidRequest bidRequest = givenBidRequest();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(BidderError.badInput("No valid impressions found"));
    }

    @Test
    public void makeHttpRequestsShouldGroupImpressionsByPublisherId() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp("imp1", givenImpExt("publisherId1", "slot1")),
                givenImp("imp2", givenImpExt("publisherId2", "slot2")),
                givenImp("imp3", givenImpExt("publisherId1", "slot3")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2).satisfiesExactlyInAnyOrder(
                request -> {
                    assertThat(request.getImpIds()).containsExactlyInAnyOrder("imp1", "imp3");
                    assertThat(request.getPayload().getId()).isEqualTo("testBidRequestId_publisherId1");
                },
                request -> {
                    assertThat(request.getImpIds()).containsExactlyInAnyOrder("imp2");
                    assertThat(request.getPayload().getId()).isEqualTo("testBidRequestId_publisherId2");
                }
        );
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorAndRequestWithOtherImpressionsIfThereAreImpressionsWithErrors() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp("imp1", givenImpExt("publisherId1", "slot1")),
                givenImp("imp2", givenImpExt("publisherId2", "slot2")),
                givenImp("invalidImp", mapper.createObjectNode().put("bidder", "Invalid imp.ext.bidder")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getValue()).hasSize(2).satisfiesExactlyInAnyOrder(
                request -> {
                    assertThat(request.getImpIds()).containsExactlyInAnyOrder("imp1");
                    assertThat(request.getPayload().getId()).isEqualTo("testBidRequestId_publisherId1");
                },
                request -> {
                    assertThat(request.getImpIds()).containsExactlyInAnyOrder("imp2");
                    assertThat(request.getPayload().getId()).isEqualTo("testBidRequestId_publisherId2");
                }
        );
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseHasInvalidStatus() {
        // given
        final BidderCall<BidRequest> httpCall = BidderCall.succeededHttp(
                null,
                HttpResponse.of(HttpResponseStatus.INTERNAL_SERVER_ERROR.code(), HeadersMultiMap.headers(), null),
                null);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).containsExactly(
                BidderError.badServerResponse("unexpected status code: 500. Run with request.debug = 1 for more info"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseHasInvalidBody() {
        // given
        final BidderCall<BidRequest> httpCall = BidderCall.succeededHttp(
                null,
                HttpResponse.of(HttpResponseStatus.CREATED.code(), HeadersMultiMap.headers(), "\"Invalid body\""),
                null);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1).allSatisfy(error -> {
            assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
            assertThat(error.getMessage()).startsWith("Failed to parse response as BidResponse: ");
        });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfThereAreNoBids() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall();

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badServerResponse("No valid bids found in response"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfBidHasInvalidExt() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                Bid.builder().ext(mapper.createObjectNode().put("prebid", "Invalid")).build());

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(2).satisfiesExactlyInAnyOrder(
                error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).startsWith("Failed to deserialize ext for bid: ");
                },
                error -> assertThat(error)
                        .isEqualTo(BidderError.badServerResponse("No valid bids found in response"))
        );
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfBidDoesntHaveMediaType() {
        // given
        final ObjectNode bidExt = mapper.createObjectNode()
                .set("prebid", mapper.createObjectNode());
        final BidderCall<BidRequest> httpCall = givenHttpCall(Bid.builder().id("testBidId").ext(bidExt).build());

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).containsExactlyInAnyOrder(
                BidderError.badInput("No media type for bid testBidId"),
                BidderError.badServerResponse("No valid bids found in response")
        );
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBidWithNoErrorsForValidInput() {
        // given
        final ObjectNode bidExt = mapper.createObjectNode()
                .set("prebid", mapper.createObjectNode().put("type", "banner"));
        final Bid bid = Bid.builder().id("testBidId").ext(bidExt).build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(bid);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(
                BidderBid.of(
                        bid,
                        BidType.banner,
                        "USD"
                )
        );
    }

    @Test
    public void makeBidsShouldReturnValidBidsAndErrorsIfThereAreBothValidAndInvalidBidsInInput() {
        // given
        final ObjectNode validBidExt = mapper.createObjectNode()
                .set("prebid", mapper.createObjectNode().put("type", "banner"));
        final ObjectNode invalidBidExt = mapper.createObjectNode()
                .set("prebid", mapper.createObjectNode());
        final Bid validBid = Bid.builder().id("validBidId").ext(validBidExt).build();
        final Bid invalidBid = Bid.builder().id("invalidBidId").ext(invalidBidExt).build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(validBid, invalidBid);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).containsExactly(BidderError.badInput("No media type for bid invalidBidId"));
        assertThat(result.getValue()).containsExactly(
                BidderBid.of(
                        validBid,
                        BidType.banner,
                        "USD"
                )
        );
    }

    private static BidRequest givenBidRequest() {
        return givenBidRequest(identity());
    }

    private static BidRequest givenBidRequest(Imp... imps) {
        return givenBidRequest(identity(), imps);
    }

    private static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
                                              Imp... imps) {

        return bidRequestCustomizer
                .apply(BidRequest.builder()
                        .id("testBidRequestId")
                        .imp(List.of(imps)))
                .build();
    }

    private static Imp givenImp() {
        return givenImp(givenImpExt());
    }

    private static Imp givenImp(ObjectNode impExt) {
        return givenImp(null, impExt);
    }

    private static Imp givenImp(String impId, ObjectNode impExt) {
        return Imp.builder()
                .id(impId)
                .ext(impExt)
                .build();
    }

    private static ObjectNode givenImpExt() {
        return givenImpExt("testPublisherId", "testSlotId", null);
    }

    private static ObjectNode givenImpExt(String publisherId, String slotId) {
        return givenImpExt(publisherId, slotId, null);
    }

    private static ObjectNode givenImpExt(String publisherId, String
            slotId, Map<String, List<String>> customTargeting) {
        return mapper.valueToTree(ExtPrebid.of(
                null,
                ExtImpGoldbach.of(publisherId, slotId, customTargeting)));
    }

    private static BidderCall<BidRequest> givenHttpCall(Bid... bids) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(null).build(),
                HttpResponse.of(HttpResponseStatus.CREATED.code(), null, givenBidResponse(bids)),
                null);
    }

    private static String givenBidResponse(Bid... bids) {
        try {
            return mapper.writeValueAsString(BidResponse.builder()
                    .cur("USD")
                    .seatbid(singletonList(SeatBid.builder().bid(List.of(bids)).build()))
                    .build());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error encoding BidResponse to json: " + e);
        }
    }

}
