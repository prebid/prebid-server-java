package org.prebid.server.bidder.iqzone;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Video;
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
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.iqzone.ExtImpIqzone;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class IqzoneBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "http://localhost/prebid_server";

    private IqzoneBidder iqZoneBidder;

    @Before
    public void setUp() {
        iqZoneBidder = new IqzoneBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new IqzoneBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldConvertSingleRequestToPerImpRequests() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.id("123"),
                impBuilder -> impBuilder.id("345"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = iqZoneBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getImp)
                .allSatisfy(imps -> assertThat(imps).hasSize(1));
    }

    @Test
    public void makeHttpRequestsShouldModifyImpExtWithPlacementIdAndTypeIfPlacementIdPresentInImpExt() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder ->
                impBuilder.ext(mapper.valueToTree(
                        ExtPrebid.of(null, ExtImpIqzone.of("placementId", null)))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = iqZoneBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();

        final Map<String, JsonNode> expectedImpExtBidder = Map.of(
                "placementId", TextNode.valueOf("placementId"),
                "type", TextNode.valueOf("publisher"));

        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(
                        mapper.createObjectNode()
                                .set("bidder", mapper.valueToTree(expectedImpExtBidder)));
    }

    @Test
    public void makeHttpRequestsShouldModifyImpExtWithEndpointIdAndTypeIfEndpointIdPresentInImpExt() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder ->
                impBuilder.ext(mapper.valueToTree(
                        ExtPrebid.of(null, ExtImpIqzone.of(null, "endpointId")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = iqZoneBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();

        final Map<String, JsonNode> expectedImpExtBidder = Map.of(
                "endpointId", TextNode.valueOf("endpointId"),
                "type", TextNode.valueOf("network"));

        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(
                        mapper.createObjectNode()
                                .set("bidder", mapper.valueToTree(expectedImpExtBidder)));
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = iqZoneBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyResponseIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = iqZoneBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyResponseIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(BidResponse.builder().seatbid(null).build()));

        // when
        final Result<List<BidderBid>> result = iqZoneBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldCorrectlyProceedWithVideo() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(impBuilder -> impBuilder
                        .id("someId").video(Video.builder().build())),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("someId"))));

        // when
        final Result<List<BidderBid>> result = iqZoneBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).hasSize(1)
                .allSatisfy(value -> assertThat(value.getType()).isEqualTo(BidType.video));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldCorrectlyProceedWithNative() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(impBuilder -> impBuilder
                        .id("someId").xNative(Native.builder().build())),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("someId"))));

        // when
        final Result<List<BidderBid>> result = iqZoneBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).hasSize(1)
                .allSatisfy(value -> assertThat(value.getType()).isEqualTo(BidType.xNative));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldCorrectlyProceedWithBanner() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(impBuilder -> impBuilder
                        .id("someId").banner(Banner.builder().build())),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("someId"))));

        // when
        final Result<List<BidderBid>> result = iqZoneBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).hasSize(1)
                .allSatisfy(value -> assertThat(value.getType()).isEqualTo(BidType.banner));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfImpIdDoesNotMatchImpIdInBid() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(impBuilder -> impBuilder
                        .id("someIdThatIsDifferentFromIDInBid").xNative(Native.builder().build())),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("someId"))));

        // when
        final Result<List<BidderBid>> result = iqZoneBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to find impression for ID:");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorWhenMissingType() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(impBuilder -> impBuilder.id("someId")),
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("someId"))));

        // when
        final Result<List<BidderBid>> result = iqZoneBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Unknown impression type for ID");
                });
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("someId")
                        .ext(mapper.valueToTree(
                                ExtPrebid.of(
                                        null,
                                        ExtImpIqzone.of("somePlacementId", "someEndpointId")))))
                .build();
    }

    private static HttpCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return HttpCall.success(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }

    private static BidResponse givenBidResponse(UnaryOperator<Bid.BidBuilder>... bidCustomizers) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(Arrays.stream(bidCustomizers)
                                .map(bidCustomizer -> bidCustomizer.apply(Bid.builder()).build())
                                .collect(Collectors.toList()))
                        .build()))
                .ext(ExtBidResponse.builder().build())
                .build();
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder>... impCustomizers) {
        return givenBidRequest(identity(), List.of(impCustomizers));
    }

    private static BidRequest givenBidRequest(
            UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
            List<UnaryOperator<Imp.ImpBuilder>> impCustomizers) {

        return bidRequestCustomizer.apply(
                        BidRequest.builder()
                                .imp(impCustomizers.stream()
                                        .map(IqzoneBidderTest::givenImp)
                                        .collect(Collectors.toList())))
                .build();
    }
}
