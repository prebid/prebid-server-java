package org.prebid.server.bidder.bidmachine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
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
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.bidmachine.ExtImpBidmachine;
import org.prebid.server.util.HttpUtil;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;

public class BidmachineBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://{{HOST}}/{{PATH}}/{{SELLER_ID}}";

    private BidmachineBidder bidmachineBidder;

    @Before
    public void setUp() {
        bidmachineBidder = new BidmachineBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new BidmachineBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldCorrectlyAddHeaders() {
        // given
        final Imp firstImp = givenImp(impBuilder -> impBuilder
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpBidmachine.of("host", "pubId", "1")))));

        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().ua("someUa").dnt(5).ip("someIp").language("someLanguage").build())
                .site(Site.builder().page("somePage").build())
                .imp(singletonList(firstImp))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidmachineBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue())
                .flatExtracting(res -> res.getHeaders().entries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .contains(tuple(HttpUtil.X_OPENRTB_VERSION_HEADER.toString(), "2.5"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorsOfNotValidImps() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidmachineBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .extracting(BidderError::getMessage)
                .containsExactly("Missing bidder ext in impression with id: 123");
    }

    @Test
    public void makeHttpRequestsShouldCreateCorrectURL() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.banner(Banner.builder()
                        .format(singletonList(Format.builder().w(300).h(500).build()))
                        .build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidmachineBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactly("https://127.0.0.1/path/1");
    }

    @Test
    public void makeHttpRequestsShouldNotModifyImplIfNoPrebidIsRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.banner(Banner.builder()
                        .format(singletonList(Format.builder().w(300).h(500).build()))
                        .build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidmachineBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .containsExactly(bidRequest);
    }

    @Test
    public void makeHttpRequestsShouldModifyImplIfPrebidIsRequestAndBannerBattrDoesNotContain16() {
        // given
        final Imp imp = givenImp(impBuilder -> impBuilder
                .banner(Banner.builder()
                        .format(singletonList(Format.builder().w(300).h(500).build()))
                        .battr(singletonList(1)).build())
                .ext(mapper.valueToTree(ExtPrebid.of(
                        ExtImpPrebid.builder()
                                .isRewardedInventory(1)
                                .build(), ExtImpBidmachine.of("host", "pubId", "1")))));
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(imp))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidmachineBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getImp)
                .extracting(imps -> imps.get(0))
                .flatExtracting(currImp -> currImp.getBanner().getBattr())
                .containsExactly(1, 16);
    }

    @Test
    public void makeHttpRequestsShouldModifyImplIfPrebidIsRequestAndVideoBattrDoesNotContain16() {
        // given
        final Imp imp = givenImp(impBuilder -> impBuilder
                .video(Video.builder().battr(singletonList(1)).build())
                .ext(mapper.valueToTree(ExtPrebid.of(
                        ExtImpPrebid.builder()
                                .isRewardedInventory(1)
                                .build(), ExtImpBidmachine.of("host", "pubId", "1")))));
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(imp))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = bidmachineBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getImp)
                .extracting(imps -> imps.get(0))
                .flatExtracting(currImp -> currImp.getVideo().getBattr())
                .containsExactly(1, 16);
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = bidmachineBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfBannerIsPresentInRequestImp() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").banner(Banner.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = bidmachineBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), banner, null));
    }

    @Test
    public void makeBidsShouldAddErrorBidIfBidIdIsNotPresentInRequestImp() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = bidmachineBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors())
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).isEqualTo(
                            "ignoring bid id=null, request doesn't contain any valid impression with id=123");
                });
        assertThat(result.getValue()).isEmpty();
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder().bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                .id("123")
                .banner(Banner.builder().w(23).h(25).build())
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpBidmachine.of("127.0.0.1", "path", "1")))))
                .build();
    }

    private static BidRequest givenBidRequest(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer);
    }

    private static BidRequest givenBidRequest(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestCustomizer,
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                .imp(singletonList(givenImp(impCustomizer))))
                .build();
    }

    private static HttpCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return HttpCall.success(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
