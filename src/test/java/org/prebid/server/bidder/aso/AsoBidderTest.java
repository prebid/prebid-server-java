package org.prebid.server.bidder.aso;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.aso.ExtImpAso;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.bidder.model.BidderError.Type.bad_server_response;
import static org.prebid.server.util.HttpUtil.ACCEPT_HEADER;
import static org.prebid.server.util.HttpUtil.APPLICATION_JSON_CONTENT_TYPE;
import static org.prebid.server.util.HttpUtil.CONTENT_TYPE_HEADER;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;

public class AsoBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test-url.com?zid={{ZoneID}}";

    private final AsoBidder target = new AsoBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void shouldFailOnBidderCreation() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AsoBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedUrl() {
        // given
        final ObjectNode invalidExt = mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()));
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.id("imp1").ext(givenImpExt(1)),
                impBuilder -> impBuilder.id("imp2").ext(givenImpExt(2)),
                impBuilder -> impBuilder.id("invalid_imp3").ext(invalidExt));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(2)
                .extracting(HttpRequest::getUri)
                .containsExactly("https://test-url.com?zid=1", "https://test-url.com?zid=2");

        assertThat(result.getErrors()).hasSize(1).allSatisfy(error -> {
            assertThat(error.getMessage()).startsWith("Missing bidder ext in impression with id: invalid_imp3");
            assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
        });
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
    public void makeHttpRequestsShouldReturnExpectedBody() {
        // given
        final ObjectNode invalidExt = mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()));
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.id("imp1"),
                impBuilder -> impBuilder.id("imp2"),
                impBuilder -> impBuilder.id("invalid_imp3").ext(invalidExt));

        // when
        final Result<List<HttpRequest<BidRequest>>> results = target.makeHttpRequests(bidRequest);

        // then
        final BidRequest firstExpectedRequest = givenBidRequest(impBuilder -> impBuilder.id("imp1"));
        final BidRequest secondExpectedRequest = givenBidRequest(impBuilder -> impBuilder.id("imp2"));
        assertThat(results.getValue()).hasSize(2)
                .extracting(HttpRequest::getBody, HttpRequest::getPayload)
                .containsExactly(
                        tuple(jacksonMapper.encodeToBytes(firstExpectedRequest), firstExpectedRequest),
                        tuple(jacksonMapper.encodeToBytes(secondExpectedRequest), secondExpectedRequest));

        assertThat(results.getErrors()).hasSize(1).allSatisfy(error -> {
            assertThat(error.getMessage()).startsWith("Missing bidder ext in impression with id: invalid_imp3");
            assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
        });
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedRequestMethod() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> results = target.makeHttpRequests(bidRequest);

        // then
        assertThat(results.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getMethod()).isEqualTo(HttpMethod.POST));
        assertThat(results.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedImpIds() {
        // given
        final ObjectNode invalidExt = mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()));
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.id("imp1"),
                impBuilder -> impBuilder.id("imp2"),
                impBuilder -> impBuilder.id("invalid_imp3").ext(invalidExt));

        // when
        final Result<List<HttpRequest<BidRequest>>> results = target.makeHttpRequests(bidRequest);

        // then
        assertThat(results.getValue()).hasSize(2)
                .extracting(HttpRequest::getImpIds)
                .containsExactly(Set.of("imp1"), Set.of("imp2"));
        assertThat(results.getErrors()).hasSize(1).allSatisfy(error -> {
            assertThat(error.getMessage()).startsWith("Missing bidder ext in impression with id: invalid_imp3");
            assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
        });
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Failed to decode: Unrecognized token");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListWhenBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall();

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorAndEmptyListWhenBidTypeCanBeResolved() throws JsonProcessingException {
        // given
        final Bid invalidBid1 = Bid.builder().impid("imp_id1").ext(mapper.createObjectNode().put("prebid", 2)).build();
        final Bid invalidBid2 = Bid.builder().impid("imp_id2").ext(mapper.createObjectNode()).build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(invalidBid1, invalidBid2);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(2)
                .extracting(BidderError::getMessage, BidderError::getType)
                .containsExactly(
                        tuple("Failed to get type of bid \"imp_id1\"", bad_server_response),
                        tuple("Failed to get type of bid \"imp_id2\"", bad_server_response));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBidWithResolvedMacros() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = mapper.valueToTree(ExtPrebid.of(
                ExtBidPrebid.builder().type(BidType.banner).build(), null));
        final Bid givenBid = Bid.builder()
                .impid("imp_id")
                .price(BigDecimal.valueOf(3.32))
                .nurl("nurl_${AUCTION_PRICE}_nurl")
                .adm("adm_${AUCTION_PRICE}_adm")
                .ext(bidExt)
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBid);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).first()
                .satisfies(bid -> {
                    assertThat(bid.getBidCurrency()).isEqualTo("USD");
                    assertThat(bid.getType()).isEqualTo(BidType.banner);
                    assertThat(bid.getBid().getAdm()).isEqualTo("adm_3.32_adm");
                    assertThat(bid.getBid().getNurl()).isEqualTo("nurl_3.32_nurl");
                    assertThat(bid.getBid().getPrice()).isEqualTo(BigDecimal.valueOf(3.32));
                });
    }

    @Test
    public void makeBidsShouldReturnBidWithResolvedMacrosWhenPriceIsEmpty() throws JsonProcessingException {
        // given
        final ObjectNode bidExt = mapper.valueToTree(ExtPrebid.of(
                ExtBidPrebid.builder().type(BidType.banner).build(), null));
        final Bid givenBid = Bid.builder()
                .impid("imp_id")
                .price(null)
                .nurl("nurl_${AUCTION_PRICE}_nurl")
                .adm("adm_${AUCTION_PRICE}_adm")
                .ext(bidExt)
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBid);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).first()
                .satisfies(bid -> {
                    assertThat(bid.getBidCurrency()).isEqualTo("USD");
                    assertThat(bid.getType()).isEqualTo(BidType.banner);
                    assertThat(bid.getBid().getAdm()).isEqualTo("adm_0_adm");
                    assertThat(bid.getBid().getNurl()).isEqualTo("nurl_0_nurl");
                    assertThat(bid.getBid().getPrice()).isNull();
                });
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder>... impCustomizers) {
        return BidRequest.builder()
                .imp(Arrays.stream(impCustomizers).map(AsoBidderTest::givenImp).toList())
                .build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder().id("imp_id").ext(givenImpExt(1))).build();
    }

    private static ObjectNode givenImpExt(int zone) {
        return mapper.valueToTree(ExtPrebid.of(null, ExtImpAso.of(zone)));
    }

    private static String givenBidResponse(Bid... bids) throws JsonProcessingException {
        return mapper.writeValueAsString(BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder().bid(List.of(bids)).build()))
                .build());
    }

    private static BidderCall<BidRequest> givenHttpCall(Bid... bids) throws JsonProcessingException {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(null).build(),
                HttpResponse.of(200, null, givenBidResponse(bids)),
                null);
    }

    private static BidderCall<BidRequest> givenHttpCall(String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(null).build(),
                HttpResponse.of(200, null, body),
                null);
    }

}
