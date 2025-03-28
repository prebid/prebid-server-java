package org.prebid.server.bidder.zeta_global_ssp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
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
import org.prebid.server.bidder.theadx.TheadxBidder;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.zeta_global_ssp.ExtImpZetaGlobalSSP;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.bidder.model.BidderError.Type.bad_server_response;
import static org.prebid.server.util.HttpUtil.ACCEPT_HEADER;
import static org.prebid.server.util.HttpUtil.APPLICATION_JSON_CONTENT_TYPE;
import static org.prebid.server.util.HttpUtil.CONTENT_TYPE_HEADER;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;

public class ZetaGlobalSspBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test-url.com/{{AccountID}}";

    private final ZetaGlobalSspBidder target = new ZetaGlobalSspBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void shouldFailOnBidderCreation() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TheadxBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtMissing() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(List.of(Imp.builder()
                        .id("imp1")
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getMessage()).contains("Missing bidder ext in impression with id: imp1");
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldCreateSingleRequestAndRemoveImpExt() {
        // given
        final Imp imp1 = givenImp(imp -> imp.id("imp1").ext(givenImpExt(11)));
        final Imp imp2 = givenImp(imp -> imp.id("imp2").ext(givenImpExt(44)));
        final BidRequest bidRequest = BidRequest.builder().imp(List.of(imp1, imp2)).build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1);
        final HttpRequest<BidRequest> httpRequest = result.getValue().getFirst();

        assertThat(httpRequest.getUri()).isEqualTo("https://test-url.com/11");

        final BidRequest payload = httpRequest.getPayload();
        assertThat(payload.getImp())
                .extracting(Imp::getExt)
                .containsExactly(null, givenImpExt(44));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedHeaders2() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity()).toBuilder()
                .build();

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
    public void makeBidsShouldReturnErrorIfResponseBodyInvalid() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("invalid-response-body");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getMessage()).contains("Failed to decode:");
                    assertThat(error.getType()).isEqualTo(bad_server_response);
                });
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfSeatBidIsNullOrEmpty() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall =
                givenHttpCall(mapper.writeValueAsString(BidResponse.builder().cur("USD").build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfCannotResolveBidType() throws JsonProcessingException {
        // given
        final Bid bid = Bid.builder().impid("imp1").ext(mapper.createObjectNode()).build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder().bid(List.of(bid)).build()))
                .build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getMessage()).contains("Failed to parse impression \"imp1\" mediatype");
                    assertThat(error.getType()).isEqualTo(bad_server_response);
                });
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfTypeParsedProperly() throws JsonProcessingException {
        // given
        final ObjectNode extWithPrebidType = mapper.createObjectNode();
        extWithPrebidType.putObject("prebid").put("type", "banner");
        final Bid validBid = Bid.builder().impid("imp1").ext(extWithPrebidType).build();

        final BidResponse bidResponse = BidResponse.builder()
                .cur("USD")
                .seatbid(List.of(SeatBid.builder().bid(List.of(validBid)).build()))
                .build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(bidResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        final BidderBid bidderBid = result.getValue().getFirst();
        assertThat(bidderBid.getBid().getImpid()).isEqualTo("imp1");
        assertThat(bidderBid.getType()).isEqualTo(BidType.banner);
        assertThat(bidderBid.getBidCurrency()).isEqualTo("USD");
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder>... impCustomizers) {
        return BidRequest.builder()
                .imp(Arrays.stream(impCustomizers).map(ZetaGlobalSspBidderTest::givenImp).toList())
                .build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder().id("imp_id").ext(givenImpExt(11))).build();
    }

    private static ObjectNode givenImpExt(Integer sid) {
        return mapper.valueToTree(ExtPrebid.of(null, ExtImpZetaGlobalSSP.of(sid)));
    }

    private static BidderCall<BidRequest> givenHttpCall(String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(null).build(),
                HttpResponse.of(200, null, body),
                null);
    }

}
