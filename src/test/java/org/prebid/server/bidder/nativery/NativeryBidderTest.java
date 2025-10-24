package org.prebid.server.bidder.nativery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.Endpoint;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.nativery.ExtImpNativery;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;

import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.proto.openrtb.ext.response.BidType.audio;
import static org.prebid.server.util.HttpUtil.ACCEPT_HEADER;
import static org.prebid.server.util.HttpUtil.APPLICATION_JSON_CONTENT_TYPE;
import static org.prebid.server.util.HttpUtil.CONTENT_TYPE_HEADER;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;

public class NativeryBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.com/test";
    private static final String DEFAULT_CURRENCY = "EUR";
    private static final String NATIVERY_ERROR_HEADER = "X-Nativery-Error";

    private final NativeryBidder target = new NativeryBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new NativeryBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldMakeOneRequestPerImp() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(
                        givenImp(UnaryOperator.identity()),
                        givenImp(imp -> imp.id("321").ext(mapper.valueToTree(ExtPrebid
                                .of(null, ExtImpNativery.of("widget2")))))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getImp)
                .extracting(List::size)
                .containsOnly(1);
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getId)
                .containsExactlyInAnyOrder("123", "321");
    }

    @Test
    public void makeHttpRequestsShouldIncludeImpIds() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.id("imp1"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getId)
                .containsExactly("imp1");
    }

    @Test
    public void makeHttpRequestsShouldUseCorrectUri() {
        // given
        final BidRequest bidRequest = givenBidRequest(UnaryOperator.identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly(ENDPOINT_URL);
    }

    @Test
    public void makeHttpRequestsShouldSetExtWithWidgetId() {
        // given
        final BidRequest bidRequest = givenBidRequest(UnaryOperator.identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .extracting(ext -> ext.getProperty("nativery").path("widgetId").asText())
                .containsOnly("widget1");
    }

    @Test
    public void makeHttpRequestsShouldPreserveOriginalExtFields() {
        // given
        final ObjectNode extNode = mapper.createObjectNode();
        extNode.put("accountId", "acc-123");

        final BidRequest bidRequest = givenBidRequest(
                requestBuilder -> {
                    try {
                        return requestBuilder.ext(
                                mapper.readValue(mapper.writeValueAsString(extNode), ExtRequest.class)
                        );
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                },
                UnaryOperator.identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();

        final ObjectNode resultingExt = mapper.convertValue(
                result.getValue().get(0).getPayload().getExt(), ObjectNode.class);

        assertThat(resultingExt.path("accountId").asText()).isEqualTo("acc-123");
        assertThat(resultingExt.path("nativery").path("widgetId").asText()).isEqualTo("widget1");
    }

    @Test
    public void makeHttpRequestsShouldSetExtWithAmpTrue() {
        // given
        final ObjectNode extNode = mapper.createObjectNode();
        final ObjectNode prebidNode = mapper.createObjectNode();
        final ObjectNode serverNode = mapper.createObjectNode();
        serverNode.put("endpoint", Endpoint.openrtb2_amp.value());
        prebidNode.set("server", serverNode);
        extNode.set("prebid", prebidNode);
        final BidRequest bidRequest = givenBidRequest(
                requestBuilder -> {
                    try {
                        return requestBuilder.ext(
                                mapper.readValue(mapper.writeValueAsString(extNode), ExtRequest.class)
                        );
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                },
                UnaryOperator.identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getExt)
                .extracting(ext -> ext.getProperty("nativery").path("isAmp").asBoolean())
                .containsOnly(true);
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCannotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder ->
                impBuilder.ext(mapper.createObjectNode().set("bidder", mapper.createArrayNode())));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors())
                .hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getMessage())
                            .startsWith("Failed to deserialize Nativery extension:");
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldNotSetCurrencyIfNotProvided() {
        // given
        final BidRequest bidRequest = givenBidRequest(UnaryOperator.identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getCur)
                .allSatisfy(cur -> assertThat(cur).isNull());
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(UnaryOperator.identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getHeaders)
                .satisfies(headers -> assertThat(headers.get(CONTENT_TYPE_HEADER))
                        .isEqualTo(APPLICATION_JSON_CONTENT_TYPE))
                .satisfies(headers -> assertThat(headers.get(ACCEPT_HEADER))
                        .isEqualTo(APPLICATION_JSON_VALUE));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorWhenStatusIs204WithErrorHeader() {
        // given
        final BidderCall<BidRequest> httpCall =
                givenHttpCallWithHeaders(204, Map.of(NATIVERY_ERROR_HEADER, "test error"));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getMessage()).isEqualTo("Nativery Error: test error.");
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                });
    }

    @Test
    public void makeBidsShouldReturnErrorWhenStatusIs204WithoutErrorHeader() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCallWithHeaders(204, null);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getMessage()).isEqualTo("No Content");
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                });
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getMessage()).startsWith("Failed to decode:");
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                });
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBid() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(bidBuilder -> bidBuilder
                        .impid("123")
                        .ext(mapper.valueToTree(Map.of("nativery", Map.of("bid_ad_media_type", "banner"))))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.banner);
    }

    @Test
    public void makeBidsShouldReturnVideoBid() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(bidBuilder -> bidBuilder
                        .impid("123")
                        .ext(mapper.valueToTree(Map.of("nativery", Map.of("bid_ad_media_type", "video"))))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.video);
    }

    @Test
    public void makeBidsShouldReturnNativeBid() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(bidBuilder -> bidBuilder
                        .impid("123")
                        .ext(mapper.valueToTree(Map.of("nativery", Map.of("bid_ad_media_type", "native"))))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.xNative);
    }

    @Test
    public void makeBidsShouldHandleUnsupportedBidType() throws JsonProcessingException {
        // given
        final ObjectNode bidExtNativery = mapper.createObjectNode().put("bid_ad_media_type", "audio");
        final ObjectNode bidExt = mapper.createObjectNode()
                .putPOJO("prebid", ExtBidPrebid.builder().type(audio).build())
                .set("nativery", bidExtNativery);

        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(bidBuilder -> bidBuilder.ext(bidExt).impid("123")));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getMessage())
                            .contains("unrecognized bid_ad_media_type in response from nativery: audio");
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                });
    }

    @Test
    public void makeBidsShouldAddMetadataFromNativeryExt() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(bidBuilder -> bidBuilder
                        .impid("123")
                        .ext(mapper.valueToTree(Map.of("nativery",
                                Map.of("bid_ad_media_type", "native", "bid_adv_domains", List.of("domain.com")))))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .hasSize(1);

        final BidderBid bidderBid = result.getValue().get(0);
        final JsonNode extNode = bidderBid.getBid().getExt();
        final JsonNode metaNode = extNode.path("prebid").path("meta");

        assertThat(metaNode.path("mediaType").asText()).isEqualTo("native");
        assertThat(metaNode.path("advertiserDomains").isArray()).isTrue();
        assertThat(metaNode.path("advertiserDomains").get(0).asText()).isEqualTo("domain.com");
    }

    @Test
    public void makeBidsShouldReturnErrorForInvalidMediaType() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(bidBuilder -> bidBuilder
                        .id("123")
                        .ext(mapper.valueToTree(Map.of("nativery", Map.of("bid_ad_media_type", "invalid"))))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getMessage()).startsWith("unrecognized bid_ad_media_type");
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                });
    }

    @Test
    public void makeBidsShouldReturnErrorForMissingNativeryExt() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidResponse(bidBuilder -> bidBuilder.id("123").ext(mapper.createObjectNode())));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getMessage()).isEqualTo("missing bid.ext.nativery");
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                });
    }

    private static BidRequest givenBidRequest(
            UnaryOperator<BidRequest.BidRequestBuilder> requestCustomizer,
            UnaryOperator<Imp.ImpBuilder> impCustomizer) {

        return requestCustomizer.apply(BidRequest.builder()
                        .imp(singletonList(givenImp(impCustomizer))))
                .build();
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(UnaryOperator.identity(), impCustomizer);
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("123")
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpNativery.of("widget1")))))
                .build();
    }

    private static String givenBidResponse(UnaryOperator<Bid.BidBuilder> bidCustomizer) throws JsonProcessingException {
        final BidResponse bidResponse = BidResponse.builder()
                .cur(DEFAULT_CURRENCY)
                .seatbid(singletonList(SeatBid.builder().bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
        return mapper.writeValueAsString(bidResponse);
    }

    private static BidderCall<BidRequest> givenHttpCall(String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(null).build(),
                HttpResponse.of(200, null, body),
                null);
    }

    private static BidderCall<BidRequest> givenHttpCallWithHeaders(int statusCode, Map<String, String> headers) {
        final MultiMap multiMap = MultiMap.caseInsensitiveMultiMap();
        if (headers != null) {
            headers.forEach(multiMap::add);
        }

        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(null).build(),
                HttpResponse.of(statusCode, multiMap, null),
                null);
    }
}

