package org.prebid.server.bidder.sellwild;

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
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.MultiMap;
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.sellwild.ExtImpSellwild;
import org.prebid.server.proto.openrtb.ext.response.BidType;
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

public class SellwildBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com/openrtb2/{PublisherId}";

    private final SellwildBidder target = new SellwildBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SellwildBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorAndSkipImpWhenImpExtCouldNotBeParsed() {
        // given
        final ObjectNode invalidExt = mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()));
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.id("invalid").ext(invalidExt)),
                givenImp(imp -> imp.id("valid")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).satisfiesExactly(error -> {
            assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
            assertThat(error.getMessage()).startsWith("Failed to parse bidder parameters for imp invalid");
        });
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getId)
                .containsExactly("valid");
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenBidderParamsAreMissing() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(imp -> imp.ext(mapper.createObjectNode())));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("Missing bidder parameters for imp 123"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenPartnerCodeIsBlank() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(identity(), ExtImpSellwild.of(" ", "token")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(BidderError.badInput("Missing partnerCode for imp 123"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenTokenIsMissing() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(identity(), ExtImpSellwild.of("partner", null)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(BidderError.badInput("Missing token for imp 123"));
    }

    @Test
    public void makeHttpRequestsShouldGroupImpsByPartnerCodePreservingOrder() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.id("imp1"), ExtImpSellwild.of("partnerB", "token1")),
                givenImp(imp -> imp.id("imp2"), ExtImpSellwild.of("partnerA", "token2")),
                givenImp(imp -> imp.id("imp3"), ExtImpSellwild.of("partnerB", "token3")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).satisfiesExactly(
                request -> {
                    assertThat(request.getUri()).isEqualTo("https://test.endpoint.com/openrtb2/partnerB");
                    assertThat(request.getPayload().getImp()).extracting(Imp::getId).containsExactly("imp1", "imp3");
                    assertThat(request.getImpIds()).containsExactlyInAnyOrder("imp1", "imp3");
                },
                request -> {
                    assertThat(request.getUri()).isEqualTo("https://test.endpoint.com/openrtb2/partnerA");
                    assertThat(request.getPayload().getImp()).extracting(Imp::getId).containsExactly("imp2");
                    assertThat(request.getImpIds()).containsExactly("imp2");
                });
    }

    @Test
    public void makeHttpRequestsShouldEscapePartnerCodeInUrl() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(identity(), ExtImpSellwild.of("a b/c?d", "token")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://test.endpoint.com/openrtb2/a%20b%2Fc%3Fd");
    }

    @Test
    public void makeHttpRequestsShouldLeaveImpExtUntouched() {
        // given
        final ObjectNode impExt = mapper.valueToTree(ExtPrebid.of(null, ExtImpSellwild.of("partner", "secret")));
        impExt.put("tid", "transaction-id");
        final BidRequest bidRequest = givenBidRequest(givenImp(imp -> imp.ext(impExt)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsExactly(impExt);
        assertThat(impExt.at("/bidder/token").asText()).isEqualTo("secret");
        assertThat(impExt.at("/bidder/partnerCode").asText()).isEqualTo("partner");
    }

    @Test
    public void makeHttpRequestsShouldSetExpectedHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(identity()));

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
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(givenImp(identity())), "invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).satisfiesExactly(error -> {
            assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
            assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token");
        });
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(givenImp(identity())), mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfSeatBidIsEmpty() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(givenImp(identity())),
                mapper.writeValueAsString(BidResponse.builder().cur("USD").build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldResolveBidTypeFromMtype() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(givenImp(identity())),
                givenBidResponse(
                        givenBid("bid1", "123", 1),
                        givenBid("bid2", "123", 2),
                        givenBid("bid4", "123", 4)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(bidderBid -> bidderBid.getBid().getId(), BidderBid::getType)
                .containsExactly(
                        tuple("bid1", BidType.banner),
                        tuple("bid2", BidType.video),
                        tuple("bid4", BidType.xNative));
    }

    @Test
    public void makeBidsShouldReturnErrorAndSkipBidWithUnsupportedMtype() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(givenImp(identity())),
                givenBidResponse(givenBid("audio", "123", 3), givenBid("valid", "123", 1)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badServerResponse("Unsupported mtype 3 for bid audio"));
        assertThat(result.getValue())
                .extracting(bidderBid -> bidderBid.getBid().getId())
                .containsExactly("valid");
    }

    @Test
    public void makeBidsShouldFallBackToImpMediaTypeWhenMtypeIsAbsent() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(
                        givenImp(imp -> imp.id("bannerImp")),
                        givenImp(imp -> imp.id("videoImp").banner(null).video(Video.builder().build())),
                        givenImp(imp -> imp.id("nativeImp").banner(null).xNative(Native.builder().build()))),
                givenBidResponse(
                        givenBid("bid1", "bannerImp", null),
                        givenBid("bid2", "videoImp", 0),
                        givenBid("bid3", "nativeImp", null)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(bidderBid -> bidderBid.getBid().getId(), BidderBid::getType)
                .containsExactly(
                        tuple("bid1", BidType.banner),
                        tuple("bid2", BidType.video),
                        tuple("bid3", BidType.xNative));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenMtypeIsAbsentAndImpIsMultiFormat() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(givenImp(imp -> imp.video(Video.builder().build()))),
                givenBidResponse(givenBid("bid1", "123", null)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).containsExactly(BidderError.badServerResponse(
                "Unable to determine media type for bid bid1: imp 123 must have exactly one of banner, video, native"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenMtypeIsAbsentAndImpIsNotFound() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(givenImp(identity())),
                givenBidResponse(givenBid("bid1", "unknown", null)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .containsExactly(BidderError.badServerResponse("unable to find imp unknown for bid bid1"));
    }

    @Test
    public void makeBidsShouldRejectBidForImpNotInOutgoingRequestEvenWhenMtypeIsSet()
            throws JsonProcessingException {

        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.id("impA"), ExtImpSellwild.of("partnerA", "tokenA")),
                givenImp(imp -> imp.id("impB"), ExtImpSellwild.of("partnerB", "tokenB")));
        final List<HttpRequest<BidRequest>> httpRequests = target.makeHttpRequests(bidRequest).getValue();
        assertThat(httpRequests).hasSize(2);

        final BidderCall<BidRequest> partnerACall = BidderCall.succeededHttp(
                httpRequests.getFirst(),
                HttpResponse.of(200, null, givenBidResponse(
                        givenBid("bidA", "impA", 1),
                        givenBid("bidB", "impB", 1))),
                null);

        // when
        final Result<List<BidderBid>> result = target.makeBids(partnerACall, bidRequest);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badServerResponse("unable to find imp impB for bid bidB"));
        assertThat(result.getValue())
                .extracting(bidderBid -> bidderBid.getBid().getId(), BidderBid::getType)
                .containsExactly(tuple("bidA", BidType.banner));
    }

    @Test
    public void makeBidsShouldUseCurrencyFromResponse() throws JsonProcessingException {
        // given
        final BidResponse bidResponse = BidResponse.builder()
                .cur("EUR")
                .seatbid(singletonList(SeatBid.builder()
                        .seat("sellwild")
                        .bid(singletonList(givenBid("bid1", "123", 1)))
                        .build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(givenImp(identity())), mapper.writeValueAsString(bidResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(givenBid("bid1", "123", 1), BidType.banner, "EUR"));
    }

    private static BidRequest givenBidRequest(Imp... imps) {
        return BidRequest.builder().id("request-id").imp(asList(imps)).build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return givenImp(impCustomizer, ExtImpSellwild.of("partner", "token"));
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer, ExtImpSellwild extImp) {
        return impCustomizer.apply(Imp.builder()
                        .id("123")
                        .banner(Banner.builder().w(300).h(250).build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, extImp))))
                .build();
    }

    private static Bid givenBid(String id, String impId, Integer mtype) {
        return Bid.builder().id(id).impid(impId).mtype(mtype).build();
    }

    private static String givenBidResponse(Bid... bids) throws JsonProcessingException {
        return mapper.writeValueAsString(BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder().seat("sellwild").bid(asList(bids)).build()))
                .build());
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
