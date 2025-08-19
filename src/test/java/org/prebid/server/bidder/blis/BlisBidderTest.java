package org.prebid.server.bidder.blis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.blis.ExtImpBlis;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.bidder.model.BidderError.badServerResponse;
import static org.prebid.server.util.HttpUtil.ACCEPT_HEADER;
import static org.prebid.server.util.HttpUtil.APPLICATION_JSON_CONTENT_TYPE;
import static org.prebid.server.util.HttpUtil.CONTENT_TYPE_HEADER;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;

@ExtendWith(MockitoExtension.class)
public class BlisBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://endpoint.com/?spid={{SupplyId}}";

    private final BlisBidder target = new BlisBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new BlisBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).startsWith("Error parsing imp.ext:");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldUseFirstImpExtToResolveUrl() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.ext(givenImpExt("spid1")),
                imp -> imp.ext(givenImpExt("spid2")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest); // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getUri)
                .isEqualTo("https://endpoint.com/?spid=spid1");
    }

    @Test
    public void makeHttpRequestsShouldUseFirstImpExtToSetHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.ext(givenImpExt("spid1")),
                imp -> imp.ext(givenImpExt("spid2")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getHeaders)
                .satisfies(headers -> {
                    assertThat(headers.get("X-Supply-Partner-Id")).isEqualTo("spid1");
                    assertThat(headers.get(CONTENT_TYPE_HEADER)).isEqualTo(APPLICATION_JSON_CONTENT_TYPE);
                    assertThat(headers.get(ACCEPT_HEADER)).isEqualTo(APPLICATION_JSON_VALUE);
                });
    }

    @Test
    public void makeHttpRequestsShouldSetPayloadAndBody() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.ext(givenImpExt("spid1")),
                imp -> imp.ext(givenImpExt("spid2")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).first()
                .satisfies(httpRequest -> {
                    assertThat(httpRequest.getPayload()).isEqualTo(bidRequest);
                    assertThat(httpRequest.getBody()).isEqualTo(jacksonMapper.encodeToBytes(bidRequest));
                });
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("invalid_json");

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
    public void makeBidsShouldReturnBannerBidWithMacrosResolved() throws JsonProcessingException {
        // given
        final Bid bannerBid = givenBid(1, BigDecimal.valueOf(1.23));
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(bannerBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        final Bid expectedBid = bannerBid.toBuilder()
                .adm("adm_1.23")
                .nurl("nurl_1.23")
                .burl("burl_1.23")
                .build();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(expectedBid, BidType.banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidWithMacrosResolved() throws JsonProcessingException {
        // given
        final Bid videoBid = givenBid(2, BigDecimal.valueOf(2.34));
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(videoBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        final Bid expectedBid = videoBid.toBuilder()
                .adm("adm_2.34")
                .nurl("nurl_2.34")
                .burl("burl_2.34")
                .build();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(expectedBid, BidType.video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnNativeBidWithMacrosResolved() throws JsonProcessingException {
        // given
        final Bid nativeBid = givenBid(4, BigDecimal.valueOf(3.45));
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(nativeBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        final Bid expectedBid = nativeBid.toBuilder()
                .adm("adm_3.45")
                .nurl("nurl_3.45")
                .burl("burl_3.45")
                .build();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(expectedBid, BidType.xNative, "USD"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfMtypeIsUnsupported() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(givenBid(3, BigDecimal.ONE)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(badServerResponse("Failed to parse media type of impression ID impId"));
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder>... impCustomizers) {
        final List<Imp> imps = Arrays.stream(impCustomizers)
                .map(BlisBidderTest::givenImp)
                .toList();
        return BidRequest.builder().imp(imps).build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder().id("impId")).build();
    }

    private static ObjectNode givenImpExt(String spid) {
        return mapper.valueToTree(ExtPrebid.of(null, ExtImpBlis.of(spid)));
    }

    private static Bid givenBid(Integer mtype, BigDecimal price) {
        return Bid.builder()
                .id("bidId")
                .impid("impId")
                .price(price)
                .mtype(mtype)
                .adm("adm_${AUCTION_PRICE}")
                .nurl("nurl_${AUCTION_PRICE}")
                .burl("burl_${AUCTION_PRICE}")
                .build();
    }

    private static String givenBidResponse(Bid... bids) throws JsonProcessingException {
        return mapper.writeValueAsString(BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder().bid(List.of(bids)).build()))
                .build());
    }

    private static BidderCall<BidRequest> givenHttpCall(String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
