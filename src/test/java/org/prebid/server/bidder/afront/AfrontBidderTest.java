package org.prebid.server.bidder.afront;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
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
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.afront.ExtImpAfront;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.util.HttpUtil.ACCEPT_HEADER;
import static org.prebid.server.util.HttpUtil.APPLICATION_JSON_CONTENT_TYPE;
import static org.prebid.server.util.HttpUtil.CONTENT_TYPE_HEADER;
import static org.prebid.server.util.HttpUtil.USER_AGENT_HEADER;
import static org.prebid.server.util.HttpUtil.X_FORWARDED_FOR_HEADER;
import static org.prebid.server.util.HttpUtil.X_OPENRTB_VERSION_HEADER;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;

public class AfrontBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.com/?aid={{AccountId}}&sid={{SourceId}}";

    private final AfrontBidder target = new AfrontBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AfrontBidder("invalid_url", jacksonMapper));
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
                    assertThat(error.getMessage()).startsWith("ext.bidder not provided");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldUseFirstImpExtToResolveUrl() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.ext(givenImpExt("acc1", "src1")),
                imp -> imp.ext(givenImpExt("acc2", "src2")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getUri)
                .isEqualTo("https://test.com/?aid=acc1&sid=src1");
    }

    @Test
    public void makeHttpRequestsShouldRemoveExtFromAllImps() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.ext(givenImpExt("acc1", "src1")),
                imp -> imp.ext(givenImpExt("acc2", "src2")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsOnlyNulls();
    }

    @Test
    public void makeHttpRequestsShouldSetCorrectHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity())
                .toBuilder()
                .device(Device.builder()
                        .ua("Test User Agent")
                        .ip("ip")
                        .ipv6("ipv6")
                        .build())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getHeaders)
                .satisfies(headers -> {
                    assertThat(headers.get(CONTENT_TYPE_HEADER)).isEqualTo(APPLICATION_JSON_CONTENT_TYPE);
                    assertThat(headers.get(ACCEPT_HEADER)).isEqualTo(APPLICATION_JSON_VALUE);
                    assertThat(headers.get(X_OPENRTB_VERSION_HEADER)).isEqualTo("2.5");
                    assertThat(headers.get(USER_AGENT_HEADER)).isEqualTo("Test User Agent");
                    assertThat(headers.get(X_FORWARDED_FOR_HEADER)).isEqualTo("ipv6");
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
    public void makeBidsShouldReturnBannerBidWhenImpHasNoVideoOrNative() throws JsonProcessingException {
        // given
        final Bid bannerBid = givenBid(imp -> imp.id("banner_imp"));

        final BidRequest bidRequest = BidRequest.builder()
                .imp(List.of(
                        givenImp(imp -> imp.id("banner_imp"))))
                .build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(bannerBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .containsOnly(BidderBid.of(bannerBid, BidType.banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidWhenImpHasVideo() throws JsonProcessingException {
        // given
        final Bid videoBid = givenBid(imp -> imp.id("video_imp"));

        final BidRequest bidRequest = BidRequest.builder()
                .imp(List.of(
                        givenImp(imp -> imp.id("video_imp").video(Video.builder().build()))))
                .build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(videoBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .containsOnly(BidderBid.of(videoBid, BidType.video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnNativeBidWhenImpHasNative() throws JsonProcessingException {
        // given
        final Bid nativeBid = givenBid(imp -> imp.id("native_imp"));

        final BidRequest bidRequest = BidRequest.builder()
                .imp(List.of(givenImp(imp -> imp.id("native_imp").xNative(Native.builder().build()))))
                .build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidResponse(nativeBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .containsOnly(BidderBid.of(nativeBid, BidType.xNative, "USD"));
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder>... impCustomizers) {
        final List<Imp> imps = Arrays.stream(impCustomizers)
                .map(AfrontBidderTest::givenImp)
                .toList();
        return BidRequest.builder().imp(imps).build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                .id("impId")
                .ext(givenImpExt("accountId", "sourceId")))
                .build();
    }

    private static ObjectNode givenImpExt(String accountId, String sourceId) {
        return mapper.valueToTree(ExtPrebid.of(null, ExtImpAfront.of(accountId, sourceId)));
    }

    private static Bid givenBid(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return Bid.builder().impid(givenImp(impCustomizer).getId()).price(BigDecimal.ONE).build();
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
