package org.prebid.server.bidder.operaads;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Video;
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
import org.prebid.server.proto.openrtb.ext.request.operaads.ExtImpOperaads;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

public class OperaadsBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://randomurl.com/{{AccountId}}/{{PublisherId}}";

    private OperaadsBidder operaadsBidder;

    @Before
    public void setUp() {
        operaadsBidder = new OperaadsBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new OperaadsBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfDeviceIsAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder.device(null), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = operaadsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("Request is missing device OS information"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfDeviceOsIsAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder ->
                bidRequestBuilder.device(Device.builder().build()), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = operaadsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("Request is missing device OS information"));
    }

    @Test
    public void makeHttpRequestsShouldTakeSizesFromFormatIfBannerSizesNotExists() {
        // given
        final Banner banner = Banner.builder().format(singletonList(Format.builder().h(1).w(1).build())).build();
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.banner(banner));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = operaadsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .containsExactly(givenImp(identity()).toBuilder()
                        .tagid("placementId")
                        .banner(banner.toBuilder().w(1).h(1).build())
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfBannerHasNoSizeParametersAndFormatIsEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.banner(Banner.builder().build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = operaadsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(BidderError.badInput("Size information missing for banner"));
    }

    @Test
    public void makeHttpRequestsShouldNotModifyNativeIfNativeIsAbsentInNativeRequest() throws JsonProcessingException {
        // given
        final ObjectNode nativeRequestNode = mapper.createObjectNode().set("native", TextNode.valueOf("test"));
        final String nativeRequest = mapper.writeValueAsString(nativeRequestNode);
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .xNative(Native.builder().request(nativeRequest).build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = operaadsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getXNative)
                .containsExactly(Native.builder().request(nativeRequest).build());
    }

    @Test
    public void makeHttpRequestsShouldCorrectlyResolveNative() throws JsonProcessingException {
        // given
        final ObjectNode nativeRequestNode = mapper.createObjectNode();
        nativeRequestNode.set("native", mapper.createObjectNode().set("test", TextNode.valueOf("test")));
        nativeRequestNode.set("junk", TextNode.valueOf("junk"));
        final String nativeRequest = mapper.writeValueAsString(nativeRequestNode);
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .xNative(Native.builder().request(nativeRequest).build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = operaadsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getXNative)
                .containsExactly(Native.builder()
                        .request(mapper.writeValueAsString(mapper.createObjectNode().set("native",
                                mapper.createObjectNode().set("test", TextNode.valueOf("test")))))
                        .build());
    }

    @Test
    public void makeHttpRequestsReturnErrorIfNativeCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .xNative(Native.builder().request("invalid_native").build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = operaadsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(bidderError -> {
                    assertThat(bidderError.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(bidderError.getMessage()).startsWith("Unrecognized token");
                });
    }

    @Test
    public void makeHttpRequestsShouldCorrectlyAddHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = operaadsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
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
        final BidRequest bidRequest = givenBidRequest(identity(),
                impBuilder -> impBuilder
                        .id("234")
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))),
                impBuilder -> impBuilder
                        .id("123")
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpOperaads.of(
                                "placementId", "endpointId", "publisherId")))));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = operaadsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .containsExactly(givenImp(impBuilder -> impBuilder.tagid("placementId")));
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("Missing bidder ext in impression with id: 234"));
    }

    @Test
    public void makeHttpRequestsShouldCreateCorrectURL() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = operaadsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactly("https://randomurl.com/endpointId/publisherId");
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = operaadsBidder.makeBids(httpCall, null);

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
        final Result<List<BidderBid>> result = operaadsBidder.makeBids(httpCall, null);

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
        final Result<List<BidderBid>> result = operaadsBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfVideoIsPresentInRequestImp() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.video(Video.builder().build()));
        final HttpCall<BidRequest> httpCall = givenHttpCall(bidRequest, mapper.writeValueAsString(
                givenBidResponse(bidBuilder -> bidBuilder.impid("123").price(BigDecimal.ONE))));

        // when
        final Result<List<BidderBid>> result = operaadsBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(
                BidderBid.of(Bid.builder().price(BigDecimal.ONE).impid("123").build(), video, null));
    }

    @Test
    public void makeBidsShouldReturnNativeBidIfNativeIsPresentInRequestImp() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.xNative(Native.builder().build()));
        final HttpCall<BidRequest> httpCall = givenHttpCall(bidRequest, mapper.writeValueAsString(
                givenBidResponse(bidBuilder -> bidBuilder.impid("123").price(BigDecimal.ONE))));

        // when
        final Result<List<BidderBid>> result = operaadsBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(
                BidderBid.of(Bid.builder().price(BigDecimal.ONE).impid("123").build(), xNative, null));
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfNativeAndVideoIsAbsentInRequestImp() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final HttpCall<BidRequest> httpCall = givenHttpCall(bidRequest, mapper.writeValueAsString(
                givenBidResponse(bidBuilder -> bidBuilder.impid("123").price(BigDecimal.ONE))));

        // when
        final Result<List<BidderBid>> result = operaadsBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(
                BidderBid.of(Bid.builder().price(BigDecimal.ONE).impid("123").build(), banner, null));
    }

    @Test
    public void makeBidsShouldOmitBidsWithNullPrice() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final HttpCall<BidRequest> httpCall = givenHttpCall(bidRequest, mapper.writeValueAsString(
                givenBidResponse(bidBuilder -> bidBuilder.impid("123").price(null))));

        // when
        final Result<List<BidderBid>> result = operaadsBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldOmitBidsWithPriceLessOrEqualToZero() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final HttpCall<BidRequest> httpCall = givenHttpCall(bidRequest, mapper.writeValueAsString(
                givenBidResponse(bidBuilder -> bidBuilder.impid("123").price(BigDecimal.valueOf(-1)),
                        bidBuilder -> bidBuilder.impid("123").price(BigDecimal.ZERO))));

        // when
        final Result<List<BidderBid>> result = operaadsBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    private static BidRequest givenBidRequest(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestCustomizer,
            Function<Imp.ImpBuilder, Imp.ImpBuilder>... impCustomizers) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                        .imp(Arrays.stream(impCustomizers)
                                .map(OperaadsBidderTest::givenImp)
                                .collect(Collectors.toList()))
                        .device(Device.builder().os("deviceOs").build()))
                .build();
    }

    private static BidRequest givenBidRequest(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer);
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                .id("123")
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpOperaads.of("placementId",
                        "endpointId", "publisherId"))))).build();
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder>... bidCustomizers) {
        return BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(Arrays.stream(bidCustomizers)
                                .map(customizer -> customizer.apply(Bid.builder()).build())
                                .collect(Collectors.toList()))
                        .build()))
                .build();
    }

    private static HttpCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return HttpCall.success(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}

