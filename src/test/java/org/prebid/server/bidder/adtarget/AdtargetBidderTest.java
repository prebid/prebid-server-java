package org.prebid.server.bidder.adtarget;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.http.HttpMethod;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.adtarget.proto.AdtargetImpExt;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.adtarget.ExtImpAdtarget;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class AdtargetBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "http://adtelligent.com";

    private AdtargetBidder adtargetBidder;

    @Before
    public void setUp() {
        adtargetBidder = new AdtargetBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void makeHttpRequestsShouldReturnHttpRequestWithCorrectBodyHeadersAndMethod() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adtargetBidder.makeHttpRequests(bidRequest);

        // then
        final BidRequest expectedBidRequest = bidRequest
                .toBuilder()
                .imp(singletonList(bidRequest.getImp().get(0).toBuilder()
                        .bidfloor(BigDecimal.valueOf(3))
                        .ext(mapper.valueToTree(AdtargetImpExt.of(
                                ExtImpAdtarget.of(15, 1, 2, BigDecimal.valueOf(3)))))
                        .build()))
                .build();
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).extracting(HttpRequest::getMethod).containsExactly(HttpMethod.POST);
        assertThat(result.getValue()).extracting(HttpRequest::getUri).containsExactly("http://adtelligent.com?aid=15");
        assertThat(result.getValue()).flatExtracting(httpRequest -> httpRequest.getHeaders().entries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()));
        assertThat(result.getValue()).extracting(HttpRequest::getPayload).containsExactly(expectedBidRequest);
    }

    @Test
    public void makeHttpRequestShouldReturnErrorMessageWhenMediaTypeWasNotDefined() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.banner(null));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adtargetBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput(
                        "ignoring imp id=impId, Adtarget supports only Video and Banner"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder ->
                impBuilder.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adtargetBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).startsWith("ignoring imp id=impId, error while "
                            + "decoding impExt, err: Cannot deserialize instance");
                });
    }

    @Test
    public void makeHttpRequestShouldReturnErrorMessageWhenImpExtIsEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder ->
                impBuilder.ext(mapper.valueToTree(ExtPrebid.of(null, null))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adtargetBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("ignoring imp id=impId, extImpBidder is empty"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestShouldReturnHttpRequestWithErrorMessage() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(Imp.builder()
                                .id("impId")
                                .banner(Banner.builder().build())
                                .ext(mapper.valueToTree(ExtPrebid.of(null, null))).build(),
                        Imp.builder()
                                .id("impId2")
                                .banner(Banner.builder().build())
                                .ext(mapper.valueToTree(
                                        ExtPrebid.of(null, ExtImpAdtarget.of(15, 1, 2, BigDecimal.valueOf(3)))))
                                .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adtargetBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("ignoring imp id=impId, extImpBidder is empty"));
        assertThat(result.getValue()).extracting(HttpRequest::getUri).containsExactly("http://adtelligent.com?aid=15");
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).hasSize(1)
                .extracting(Imp::getId).containsExactly("impId2");
    }

    @Test
    public void makeHttpRequestShouldReturnWithBidFloorPopulatedFromImpWhenIsMissedInImpExt() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .banner(Banner.builder().build())
                        .bidfloor(BigDecimal.valueOf(16))
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpAdtarget.of(15, 1, 2, null))))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adtargetBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp).hasSize(1)
                .extracting(Imp::getBidfloor).containsExactly(BigDecimal.valueOf(16));
    }

    @Test
    public void makeHttpRequestShouldReturnTwoHttpRequestsWhenTwoImpsHasDifferentSourceId() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(Imp.builder()
                                .banner(Banner.builder().build())
                                .ext(mapper.valueToTree(
                                        ExtPrebid.of(null, ExtImpAdtarget.of(15, 1, 2, BigDecimal.valueOf(3)))))
                                .build(),
                        Imp.builder()
                                .banner(Banner.builder().build())
                                .ext(mapper.valueToTree(
                                        ExtPrebid.of(null, ExtImpAdtarget.of(16, 1, 2, BigDecimal.valueOf(3)))))
                                .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adtargetBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2);
    }

    @Test
    public void makeHttpRequestShouldSetZeroToAidParamIfSourceIdIsNull() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .banner(Banner.builder().build())
                        .ext(mapper.valueToTree(
                                ExtPrebid.of(null, ExtImpAdtarget.of(null, 1, 2, BigDecimal.valueOf(3)))))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adtargetBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("http://adtelligent.com?aid=0");
    }

    @Test
    public void makeHttpRequestShouldReturnOneHttpRequestForTowImpsWhenImpsHasSameSourceId() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(Imp.builder()
                                .banner(Banner.builder().build())
                                .ext(mapper.valueToTree(
                                        ExtPrebid.of(null, ExtImpAdtarget.of(15, 1, 2, BigDecimal.valueOf(3)))))
                                .build(),
                        Imp.builder()
                                .banner(Banner.builder().build())
                                .ext(mapper.valueToTree(
                                        ExtPrebid.of(null, ExtImpAdtarget.of(15, 1, 2, BigDecimal.valueOf(3)))))
                                .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = adtargetBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
    }

    @Test
    public void makeBidsShouldReturnBidWithoutErrors() throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.id(null)));
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<BidderBid>> result = adtargetBidder.makeBids(givenHttpCall(response), bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .containsExactly(BidderBid.of(Bid.builder().impid("impId").build(), BidType.banner, "EUR"));
    }

    @Test
    public void makeBidsShouldReturnErrorMessageWhenMatchingToBidImpWasNotFound() throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(
                givenBidResponse(bidBuilder -> bidBuilder.impid("invalidId")));
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<BidderBid>> result = adtargetBidder.makeBids(givenHttpCall(response), bidRequest);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badServerResponse(
                        "ignoring bid id=bidId, request doesn't contain any impression with id=invalidId"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBidWithErrorMessage() throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(asList(Bid.builder().id("bidId1").impid("invalidId").build(),
                                Bid.builder().id("bidId2").impid("impId").build()))
                        .build()))
                .build());
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<BidderBid>> result = adtargetBidder.makeBids(givenHttpCall(response), bidRequest);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badServerResponse(
                        "ignoring bid id=bidId1, request doesn't contain any impression with id=invalidId"));
        assertThat(result.getValue()).hasSize(1)
                .extracting(BidderBid::getBid)
                .extracting(Bid::getId).containsExactly("bidId2");
    }

    @Test
    public void makeBidsShouldReturnBidsFromDifferentSeatBidsInResponse() throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(BidResponse.builder()
                .seatbid(asList(
                        SeatBid.builder()
                                .bid(singletonList(Bid.builder().id("bidId1").impid("impId1").build()))
                                .build(),
                        SeatBid.builder()
                                .bid(singletonList(Bid.builder().id("bidId2").impid("impId2").build()))
                                .build()))
                .build());
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(Imp.builder().id("impId1").build(), Imp.builder().id("impId2").build()))
                .build();

        // when
        final Result<List<BidderBid>> result = adtargetBidder.makeBids(givenHttpCall(response), bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2)
                .extracting(BidderBid::getBid)
                .extracting(Bid::getId).containsExactly("bidId1", "bidId2");
    }

    @Test
    public void makeBidsShouldReturnBidderBidWithBannerBidTypeWhenMediaTypeInMatchedImpIsNotVideo()
            throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(givenBidResponse(identity()));
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<BidderBid>> result = adtargetBidder.makeBids(givenHttpCall(response), bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.banner);
    }

    @Test
    public void makeBidsShouldReturnBidderBidWithVideoBidTypeIfBannerAndVideoMediaTypesAreInMatchedImp()
            throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(givenBidResponse(identity()));
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.video(Video.builder().build()));

        // when
        final Result<List<BidderBid>> result = adtargetBidder.makeBids(givenHttpCall(response), bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.video);
    }

    @Test
    public void makeBidsShouldReturnEmptyBidderBidAndErrorListsIfSeatBidIsNotPresentInResponse()
            throws JsonProcessingException {
        // given
        final String response = mapper.writeValueAsString(BidResponse.builder().build());

        // when
        final Result<List<BidderBid>> result = adtargetBidder
                .makeBids(givenHttpCall(response), BidRequest.builder().build());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyBidderWithErrorWhenResponseCantBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall("{");

        // when
        final Result<List<BidderBid>> result = adtargetBidder.makeBids(httpCall, BidRequest.builder().build());

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unexpected end-of-input:");
                });
    }

    private static BidRequest givenBidRequest(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestCustomizer,
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                .imp(singletonList(givenImp(impCustomizer))))
                .user(User.builder()
                        .ext(ExtUser.builder().consent("consent").build())
                        .build())
                .regs(Regs.of(0, ExtRegs.of(1, null)))
                .build();
    }

    private static BidRequest givenBidRequest(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer);
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                .id("impId")
                .banner(Banner.builder().build())
                .ext(mapper.valueToTree(
                        ExtPrebid.of(null, ExtImpAdtarget.of(15, 1, 2, BigDecimal.valueOf(3))))))
                .build();
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .cur("EUR")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bidCustomizer.apply(Bid.builder().id("bidId").impid("impId")).build()))
                        .build()))
                .build();
    }

    private static HttpCall<BidRequest> givenHttpCall(String body) {
        return HttpCall.success(null, HttpResponse.of(200, null, body), null);
    }
}
