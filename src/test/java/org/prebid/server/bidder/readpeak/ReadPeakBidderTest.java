package org.prebid.server.bidder.readpeak;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.readpeak.ExtImpReadPeak;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

public class ReadPeakBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.com/test";

    private final ReadPeakBidder target = new ReadPeakBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ReadPeakBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldContainAllImpsInOneRequest() throws IOException {
        // given
        final Imp imp1 = givenImp(imp -> imp.id("imp1"));
        final Imp imp2 = givenImp(imp -> imp.id("imp2"));
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(imp1, imp2))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);

        final byte[] requestBody = result.getValue().get(0).getBody();
        final BidRequest capturedBidRequest = mapper.readValue(requestBody, BidRequest.class);

        assertThat(capturedBidRequest.getImp()).hasSize(2);
        assertThat(capturedBidRequest.getImp()).extracting(Imp::getId).containsExactly("imp1", "imp2");
    }

    @Test
    public void shouldMakeOneRequestWhenOneImpIsValidAndAnotherIsNot() throws IOException {
        // given
        final Imp validImp = givenImp(imp -> imp.id("validImp"));
        final Imp invalidImp = givenBadImp(imp -> imp.id("invalidImp"));
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(validImp, invalidImp))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1); // Oczekujemy jednego błędu dla niepoprawnej impresji
        assertThat(result.getValue()).hasSize(1); // Oczekujemy jednego żądania HTTP

        final byte[] requestBody = result.getValue().get(0).getBody();
        final BidRequest capturedBidRequest = mapper.readValue(requestBody, BidRequest.class);

        assertThat(capturedBidRequest.getImp()).hasSize(1);
        assertThat(capturedBidRequest.getImp()).extracting(Imp::getId).containsExactly("validImp");
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCannotBeParsed2() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder ->
                impBuilder.ext(mapper.createObjectNode().set("bidder", mapper.createArrayNode())));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();

        final List<BidderError> errors = result.getErrors();
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage())
                .startsWith("Failed to find compatible impressions for request");
    }

    @Test
    public void makeHttpRequestsShouldUseBidFloorFromImpIfValid() throws IOException {
        // given
        final BigDecimal validBidFloor = new BigDecimal("1.23");
        final String bidFloorCurrency = "USD";

        final Imp imp = Imp.builder()
                .id("123")
                .banner(Banner.builder().build())
                .bidfloor(validBidFloor)
                .bidfloorcur(bidFloorCurrency)
                .ext(mapper.valueToTree(ExtPrebid
                        .of(null, ExtImpReadPeak.of("publisherId", "siteid", null, "tagid"))))
                .build();

        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(imp))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);

        final byte[] requestBody = result.getValue().get(0).getBody();
        final BidRequest capturedBidRequest = mapper.readValue(requestBody, BidRequest.class);
        final Imp capturedImp = capturedBidRequest.getImp().get(0);

        assertThat(capturedImp.getBidfloor()).isEqualByComparingTo(validBidFloor);
    }

    @Test
    public void makeHttpRequestsShouldSetSitePublisher() throws IOException {
        // given
        final Site site = Site.builder().id("siteId").build();
        final Imp validImp = givenImp(imp -> imp.id("validImp"));
        final BidRequest bidRequest = BidRequest.builder()
                .site(site)
                .imp(Collections.singletonList(validImp))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);

        final byte[] requestBody = result.getValue().get(0).getBody();
        final BidRequest capturedBidRequest = mapper.readValue(requestBody, BidRequest.class);
        assertThat(capturedBidRequest.getSite()).isNotNull();
        assertThat(capturedBidRequest.getSite().getPublisher()).isNotNull();
        assertThat(capturedBidRequest.getSite().getPublisher().getId()).isEqualTo("publisherId");
        assertThat(capturedBidRequest.getSite().getId()).isEqualTo("siteId");
    }

    @Test
    public void makeHttpRequestsShouldSetAppPublisher() throws IOException {
        // given
        final App app = App.builder().id("appId").build();
        final Imp validImp = givenImpWithNullSideId(imp -> imp.id("validImp"));
        final BidRequest bidRequest = BidRequest.builder()
                .app(app)
                .imp(Collections.singletonList(validImp))
                .build();
        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);

        final byte[] requestBody = result.getValue().get(0).getBody();
        final BidRequest capturedBidRequest = mapper.readValue(requestBody, BidRequest.class);
        assertThat(capturedBidRequest.getApp()).isNotNull();
        assertThat(capturedBidRequest.getApp().getPublisher()).isNotNull();
        assertThat(capturedBidRequest.getApp().getPublisher().getId()).isEqualTo("publisherId");
        assertThat(capturedBidRequest.getApp().getId()).isEqualTo("appId");
    }

    @Test
    public void makeHttpRequestsShouldUseSiteIdForAppWhenSiteIdIsNotBlank() throws IOException {
        // given
        final App app = App.builder().id("appId").build();
        final Imp validImp = givenImp(imp -> imp.id("validImp"));
        final BidRequest bidRequest = BidRequest.builder()
                .app(app)
                .imp(Collections.singletonList(validImp))
                .build();
        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);

        final byte[] requestBody = result.getValue().get(0).getBody();
        final BidRequest capturedBidRequest = mapper.readValue(requestBody, BidRequest.class);
        assertThat(capturedBidRequest.getApp()).isNotNull();
        assertThat(capturedBidRequest.getApp().getPublisher()).isNotNull();
        assertThat(capturedBidRequest.getApp().getPublisher().getId()).isEqualTo("publisherId");
        assertThat(capturedBidRequest.getApp().getId()).isEqualTo("siteId");
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull2() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed2() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token 'invalid':");
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                });
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull2() throws JsonProcessingException {
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
    public void makeBidsShouldReturnBannerBid() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = mapper.createObjectNode()
                .putPOJO("prebid", ExtBidPrebid.builder().type(banner).build());
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").banner(Banner.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.ext(bidExt).impid("123").mtype(1))));
        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(banner);
    }

    @Test
    public void makeBidsShouldReturnNativeBid() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = mapper.createObjectNode()
                .putPOJO("prebid", ExtBidPrebid.builder().type(xNative).build());
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").xNative(Native.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.ext(bidExt).impid("123").mtype(4))));
        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(xNative);
    }

    @Test
    public void makeBidsShouldReturnErrorForUnsupportedMType() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = mapper.createObjectNode()
                .putPOJO("prebid", ExtBidPrebid.builder().type(banner).build());
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").banner(Banner.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.ext(bidExt).impid("123").mtype(2))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getErrors())
                .extracting(BidderError::getMessage)
                .containsExactly("Unable to fetch mediaType 2 in multi-format: 123");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorForMissingMType() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = mapper.createObjectNode()
                .putPOJO("prebid", ExtBidPrebid.builder().type(banner).build());
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").banner(Banner.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.ext(bidExt).impid("123"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isNotEmpty();
        assertThat(result.getErrors())
                .extracting(BidderError::getMessage)
                .containsExactly("Unable to fetch mediaType null in multi-format: 123");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldCreateBidExtIfMissing() throws JsonProcessingException {
        // given
        final Bid bid = Bid.builder()
                .id("1")
                .price(BigDecimal.valueOf(1.23))
                .adm("${AUCTION_PRICE}")
                .nurl("${AUCTION_PRICE}")
                .impid("123")
                .mtype(1)
                .build();

        final BidResponse bidResponse = BidResponse.builder()
                .cur("USD")
                .seatbid(List.of(SeatBid.builder()
                        .bid(List.of(bid))
                        .build()))
                .build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").banner(Banner.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(bidResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);

        final BidderBid bidderBid = result.getValue().get(0);
        final Bid updatedBid = bidderBid.getBid();

        assertThat(updatedBid.getExt()).isNotNull();
        assertThat(updatedBid.getExt().has("prebid")).isTrue();
        assertThat(updatedBid.getExt().get("prebid").has("meta")).isTrue();
    }

    private static BidRequest givenBidRequest(
            UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
            UnaryOperator<Imp.ImpBuilder> impCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                        .imp(List.of(givenImp(impCustomizer))))
                .build();
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(UnaryOperator.identity(), impCustomizer);
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                .id("123")
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpReadPeak.of("publisherId", "siteId", BigDecimal.valueOf(1.23), "someTagId"))))
                .build().toBuilder()).build();
    }

    private static Imp givenImpWithNullSideId(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                .id("123")
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpReadPeak.of("publisherId", null, BigDecimal.valueOf(1.23), "someTagId"))))
                .build().toBuilder()).build();
    }

    private static Imp givenBadImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                .id("invalidImp")
                .ext(mapper.createObjectNode().put("bidder", "invalidValue")) // nieprawidłowa wartość powodująca błąd parsowania
                .build().toBuilder()).build();
    }

    private static BidResponse givenBidResponse(UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(List.of(SeatBid.builder().bid(List.of(bidCustomizer.apply(Bid.builder().id("123")).build()))
                        .build()))
                .build();
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
