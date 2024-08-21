package org.prebid.server.bidder.readpeak;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
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
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.readpeak.ExtImpReadPeak;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidMeta;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;
import static org.prebid.server.util.HttpUtil.ACCEPT_HEADER;
import static org.prebid.server.util.HttpUtil.APPLICATION_JSON_CONTENT_TYPE;
import static org.prebid.server.util.HttpUtil.CONTENT_TYPE_HEADER;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;

public class ReadPeakBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.com/test";

    private final ReadPeakBidder target = new ReadPeakBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ReadPeakBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldContainAllImpsInOneRequest() {
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
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp).hasSize(2)
                .extracting(Imp::getId).containsExactly("imp1", "imp2");
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getBody)
                .extracting(body -> mapper.readValue(body, BidRequest.class))
                .flatExtracting(BidRequest::getImp).hasSize(2)
                .extracting(Imp::getId).containsExactly("imp1", "imp2");
    }

    @Test
    public void makeHttpRequestsShouldIncludeImpIds() {
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
        assertThat(result.getValue()).hasSize(1)
                .flatExtracting(HttpRequest::getImpIds)
                .containsExactly("imp1", "imp2");
    }

    @Test
    public void makeHttpRequestsShouldMakeOneRequestWhenOneImpIsValidAndAnotherIsNot() {
        // given
        final Imp validImp = givenImp(imp -> imp.id("validImp"));
        final Imp invalidImp = givenBadImp(imp -> imp.id("invalidImp"));
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(validImp, invalidImp))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp).hasSize(1)
                .extracting(Imp::getId).containsExactly("validImp");
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getBody)
                .extracting(body -> mapper.readValue(body, BidRequest.class))
                .flatExtracting(BidRequest::getImp).hasSize(1)
                .extracting(Imp::getId).containsExactly("validImp");
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCannotBeParsed() {
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
    public void makeHttpRequestsShouldUseBidFloorFromImpIfValid() {
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
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor)
                .containsExactly(validBidFloor);
    }

    @Test
    public void makeHttpRequestsShouldUseCorrectUri() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly(ENDPOINT_URL);
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

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
    public void makeHttpRequestsShouldSetSitePublisher() {
        // given
        final Imp validImp = givenImp(imp -> imp.id("validImp"));
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().id("siteId").build())
                .imp(Collections.singletonList(validImp))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSite)
                .extracting(Site::getId, site -> site.getPublisher().getId())
                .containsExactly(tuple("siteId", "publisherId"));
    }

    @Test
    public void makeHttpRequestsShouldSetAppPublisher() {
        // given
        final Imp validImp = givenImpWithoutSiteId(imp -> imp.id("validImp"));
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().id("appId").build())
                .imp(Collections.singletonList(validImp))
                .build();
        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getApp)
                .extracting(App::getId, app -> app.getPublisher().getId())
                .containsExactly(tuple("appId", "publisherId"));
    }

    @Test
    public void makeHttpRequestsShouldUseSiteIdForAppWhenSiteIdIsNotBlank() {
        // given
        final Imp validImp = givenImp(imp -> imp.id("validImp"));
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder().id("appId").build())
                .imp(Collections.singletonList(validImp))
                .build();
        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getApp)
                .extracting(App::getId, app -> app.getPublisher().getId())
                .containsExactly(tuple("siteId", "publisherId"));
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
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed2() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("invalid");

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
        final BidderCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBid() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(
                bidBuilder -> bidBuilder.impid("123").mtype(1)));
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
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(
                bidBuilder -> bidBuilder.impid("123").mtype(4)));
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
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(
                bidBuilder -> bidBuilder.impid("123").mtype(2)));

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
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(
                bidBuilder -> bidBuilder.mtype(null).impid("123")));

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
    public void makeBidsShouldCreateExtBidPrebidMetaWithADomains() throws JsonProcessingException {
        // given
        final Bid bid = Bid.builder()
                .id("1")
                .price(BigDecimal.valueOf(1.23))
                .adm("${AUCTION_PRICE}")
                .nurl("${AUCTION_PRICE}")
                .impid("123")
                .mtype(1)
                .adomain(List.of("domain1", "domain2"))
                .build();

        final BidResponse bidResponse = BidResponse.builder()
                .cur("USD")
                .seatbid(List.of(SeatBid.builder().bid(List.of(bid)).build()))
                .build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(bidResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();

        final ExtBidPrebidMeta expectedMeta = ExtBidPrebidMeta.builder()
                .advertiserDomains(List.of("domain1", "domain2"))
                .build();
        assertThat(result.getValue()).hasSize(1)
                .extracting(BidderBid::getBid)
                .extracting(Bid::getExt)
                .extracting(ext -> ext.get("prebid"))
                .extracting(prebid -> prebid.get("meta"))
                .containsExactly(mapper.valueToTree(expectedMeta));
    }

    @Test
    public void makeBidsShouldModifyExistingExtBidPrebidMetaWithADomains() throws JsonProcessingException {
        // given
        final Bid bid = Bid.builder()
                .id("1")
                .price(BigDecimal.valueOf(1.23))
                .adm("${AUCTION_PRICE}")
                .nurl("${AUCTION_PRICE}")
                .impid("123")
                .mtype(1)
                .adomain(List.of("domain1", "domain2"))
                .ext(mapper.createObjectNode().set(
                        "prebid",
                        mapper.valueToTree(ExtBidPrebid.builder()
                                .meta(ExtBidPrebidMeta.builder().networkId(1).build())
                                .build())))
                .build();

        final BidResponse bidResponse = BidResponse.builder()
                .cur("USD")
                .seatbid(List.of(SeatBid.builder().bid(List.of(bid)).build()))
                .build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(bidResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();

        final ExtBidPrebidMeta expectedMeta = ExtBidPrebidMeta.builder()
                .networkId(1)
                .advertiserDomains(List.of("domain1", "domain2"))
                .build();

        assertThat(result.getValue()).hasSize(1)
                .extracting(BidderBid::getBid)
                .extracting(Bid::getExt)
                .extracting(ext -> ext.get("prebid"))
                .extracting(prebid -> prebid.get("meta"))
                .containsExactly(mapper.valueToTree(expectedMeta));
    }

    private static BidRequest givenBidRequest(
            UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
            UnaryOperator<Imp.ImpBuilder> impCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                        .imp(List.of(givenImp(impCustomizer))))
                .build();
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer);
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                .id("123")
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpReadPeak.of("publisherId", "siteId", BigDecimal.valueOf(1.23), "someTagId"))))
                .build().toBuilder()).build();
    }

    private static Imp givenImpWithoutSiteId(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                .id("123")
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpReadPeak.of("publisherId", null, BigDecimal.valueOf(1.23), "someTagId"))))
                .build().toBuilder()).build();
    }

    private static Imp givenBadImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                .id("invalidImp")
                .ext(mapper.createObjectNode().put("bidder", "invalidValue"))
                .build().toBuilder()).build();
    }

    private static String givenBidResponse(UnaryOperator<Bid.BidBuilder> bidCustomizer) throws JsonProcessingException {
        final BidResponse bidResponse = BidResponse.builder()
                .cur("USD")
                .seatbid(List.of(SeatBid.builder()
                        .bid(List.of(bidCustomizer.apply(Bid.builder().id("123")).build()))
                        .build()))
                .build();

        return mapper.writeValueAsString(bidResponse);
    }

    private static BidderCall<BidRequest> givenHttpCall(String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
