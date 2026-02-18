package org.prebid.server.bidder.nativo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidSdk;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidSdkRenderer;
import org.prebid.server.proto.openrtb.ext.request.nativo.ExtImpNativo;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class NativoBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com/";

    private final NativoBidder target = new NativoBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new NativoBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnSingleDefaultRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.banner(Banner.builder().build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().get(0).getPayload()).isEqualTo(bidRequest);
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().build(),
                HttpResponse.of(200, null, "invalid_json"),
                null);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token 'invalid_json'");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListWhenBidResponseHasNullSeatbid() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.id("imp1").banner(Banner.builder().build()));
        final BidResponse bidResponse = BidResponse.builder().id("resp1").build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest, bidResponse);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListWhenBidResponseHasEmptySeatbid() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.id("imp1").banner(Banner.builder().build()));
        final BidResponse bidResponse = BidResponse.builder().id("resp1").seatbid(List.of()).build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest, bidResponse);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidWhenNoNativoRendererInRequest() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.id("imp1").banner(Banner.builder().build()));
        final Bid bid = givenBid("bid1", "imp1");
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest, givenBidResponse(singletonList(bid)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(bid, BidType.banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidWhenNoNativoRendererInRequest() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.id("imp1").video(Video.builder().build()));
        final Bid bid = givenBid("bid1", "imp1");
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest, givenBidResponse(singletonList(bid)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(bid, BidType.video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnNativeBidWhenNoNativoRendererInRequest() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.id("imp1").xNative(Native.builder().build()));
        final Bid bid = givenBid("bid1", "imp1");
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest, givenBidResponse(singletonList(bid)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(bid, BidType.xNative, "USD"));
    }

    @Test
    public void makeBidsShouldNotModifyBidWhenRendererNameDoesNotMatchNativo() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequestWithRenderer(
                imp -> imp.id("imp1").banner(Banner.builder().build()),
                "OtherRenderer", "1.0.0");
        final Bid bid = givenBid("bid1", "imp1");
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest, givenBidResponse(singletonList(bid)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(bid, BidType.banner, "USD"));
    }

    @Test
    public void makeBidsShouldEnrichBidExtWithRendererVersionWhenNativoRendererIsPresent()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequestWithRenderer(
                imp -> imp.id("imp1").banner(Banner.builder().build()),
                "NativoRenderer", "1.2.3");
        final Bid bid = givenBid("bid1", "imp1");
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest, givenBidResponse(singletonList(bid)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().get(0).getBid().getExt())
                .extracting(ext -> ext.get("prebid"))
                .extracting(prebid -> prebid.get("meta"))
                .extracting(meta -> meta.get("rendererVersion").textValue())
                .isEqualTo("1.2.3");
    }

    @Test
    public void makeBidsShouldPreserveExistingMetaFieldsWhenAddingRendererVersion()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequestWithRenderer(
                imp -> imp.id("imp1").banner(Banner.builder().build()),
                "NativoRenderer", "2.0.0");

        final ObjectNode existingExt = mapper.createObjectNode();
        existingExt.putObject("prebid").putObject("meta").put("adaptercode", "nativo");
        final Bid bid = givenBid("bid1", "imp1").toBuilder().ext(existingExt).build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest, givenBidResponse(singletonList(bid)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);

        final ObjectNode resultExt = result.getValue().get(0).getBid().getExt();
        assertThat(resultExt.get("prebid").get("meta").get("adaptercode").textValue()).isEqualTo("nativo");
        assertThat(resultExt.get("prebid").get("meta").get("rendererVersion").textValue()).isEqualTo("2.0.0");
    }

    @Test
    public void makeBidsShouldCreateMetaWhenBidExtHasNoPrebidFieldAndNativoRendererIsPresent()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequestWithRenderer(
                imp -> imp.id("imp1").banner(Banner.builder().build()),
                "NativoRenderer", "3.0.0");
        final Bid bid = givenBid("bid1", "imp1");
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest, givenBidResponse(singletonList(bid)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);

        final ObjectNode resultExt = result.getValue().get(0).getBid().getExt();
        assertThat(resultExt.get("prebid").get("meta").get("rendererVersion").textValue()).isEqualTo("3.0.0");
    }

    @Test
    public void makeBidsShouldReturnErrorAndOriginalBidWhenBidExtCannotBeParsed()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequestWithRenderer(
                imp -> imp.id("imp1").banner(Banner.builder().build()),
                "NativoRenderer", "1.0.0");

        final ObjectNode invalidExt = mapper.createObjectNode();
        invalidExt.put("prebid", "not-an-object");
        final Bid bid = givenBid("bid1", "imp1").toBuilder().ext(invalidExt).build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest, givenBidResponse(singletonList(bid)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).contains("Invalid ext passed in bid with id: bid1");
                });
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().get(0).getBid().getExt()).isEqualTo(invalidExt);
    }

    @Test
    public void makeHttpRequestsShouldPassThroughRequestWithIntegerPlacementId() {
        // given
        final ObjectNode impExt = mapper.createObjectNode();
        impExt.putObject("bidder").put("placementId", 12345678);
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("imp1").banner(Banner.builder().build()).ext(impExt).build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().get(0).getPayload().getImp().get(0).getExt()
                .path("bidder").path("placementId").intValue()).isEqualTo(12345678);
    }

    @Test
    public void makeHttpRequestsShouldPassThroughRequestWithStringPlacementId() {
        // given
        final ObjectNode impExt = mapper.createObjectNode();
        impExt.putObject("bidder").put("placementId", "string-placement-id");
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("imp1").banner(Banner.builder().build()).ext(impExt).build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().get(0).getPayload().getImp().get(0).getExt()
                .path("bidder").path("placementId").textValue()).isEqualTo("string-placement-id");
    }

    @Test
    public void makeHttpRequestsShouldPassThroughRequestWithAbsentPlacementId() {
        // given
        final ObjectNode impExt = mapper.createObjectNode();
        impExt.putObject("bidder");
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("imp1").banner(Banner.builder().build()).ext(impExt).build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().get(0).getPayload().getImp().get(0).getExt()
                .path("bidder").has("placementId")).isFalse();
    }

    @Test
    public void extImpNativoShouldDeserializeIntegerPlacementId() throws JsonProcessingException {
        // given
        final String json = "{\"placementId\": 12345678}";

        // when
        final ExtImpNativo extImpNativo = mapper.readValue(json, ExtImpNativo.class);

        // then
        assertThat(extImpNativo.getPlacementId()).isEqualTo(12345678);
    }

    @Test
    public void extImpNativoShouldDeserializeStringPlacementId() throws JsonProcessingException {
        // given
        final String json = "{\"placementId\": \"string-placement-id\"}";

        // when
        final ExtImpNativo extImpNativo = mapper.readValue(json, ExtImpNativo.class);

        // then
        assertThat(extImpNativo.getPlacementId()).isEqualTo("string-placement-id");
    }

    @Test
    public void extImpNativoShouldDeserializeAbsentPlacementIdAsNull() throws JsonProcessingException {
        // given
        final String json = "{}";

        // when
        final ExtImpNativo extImpNativo = mapper.readValue(json, ExtImpNativo.class);

        // then
        assertThat(extImpNativo.getPlacementId()).isNull();
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return BidRequest.builder()
                .imp(singletonList(impCustomizer.apply(Imp.builder().id("imp1")).build()))
                .build();
    }

    private static BidRequest givenBidRequestWithRenderer(UnaryOperator<Imp.ImpBuilder> impCustomizer,
                                                          String rendererName, String rendererVersion) {
        return BidRequest.builder()
                .imp(singletonList(impCustomizer.apply(Imp.builder().id("imp1")).build()))
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .sdk(ExtRequestPrebidSdk.of(singletonList(
                                ExtRequestPrebidSdkRenderer.of(rendererName, rendererVersion, null, null))))
                        .build()))
                .build();
    }

    private static Bid givenBid(String id, String impId) {
        return Bid.builder()
                .id(id)
                .impid(impId)
                .price(BigDecimal.valueOf(1.5))
                .adm("adm001")
                .build();
    }

    private static BidResponse givenBidResponse(List<Bid> bids) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder().bid(bids).build()))
                .build();
    }

    private BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, BidResponse bidResponse)
            throws JsonProcessingException {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, mapper.writeValueAsString(bidResponse)),
                null);
    }
}
