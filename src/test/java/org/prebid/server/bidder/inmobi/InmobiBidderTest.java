package org.prebid.server.bidder.inmobi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
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
import org.prebid.server.proto.openrtb.ext.request.inmobi.ExtImpInmobi;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;

public class InmobiBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test";
    private static final String IMP_ID = "123";
    private static final int FORMAT_W = 35;
    private static final int FORMAT_H = 37;

    private InmobiBidder inmobiBidder;

    @Before
    public void setUp() {
        inmobiBidder = new InmobiBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new InmobiBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfPlcAttributeIsNotPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpInmobi.of("   ")))));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = inmobiBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("'plc' is a required attribute for InMobi's bidder ext"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfPlcAttributeIsNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpInmobi.of(null)))));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = inmobiBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("'plc' is a required attribute for InMobi's bidder ext"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = inmobiBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("bad InMobi bidder ext"));
    }

    @Test
    public void shouldSetBannerFormatWAndHValuesToBannerIfTheyAreNotPresentInBanner() {
        // given
        final Format bannerFormat = Format.builder().w(FORMAT_W).h(FORMAT_H).build();
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.banner(Banner.builder()
                        .format(Collections.singletonList(bannerFormat))
                        .build()));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = inmobiBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(0);
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .containsExactly(Banner.builder()
                        .w(FORMAT_W)
                        .h(FORMAT_H)
                        .format(Collections.singletonList(bannerFormat))
                        .build());
    }

    @Test
    public void makeHttpRequestsShouldUpdateOnlyFirstImpression() {
        // given
        final Format bannerFormat = Format.builder().w(FORMAT_W).h(FORMAT_H).build();
        final Imp firstImp = Imp.builder()
                .banner(Banner.builder().id("firstBanner").format(Collections.singletonList(bannerFormat)).build())
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpInmobi.of("plc"))))
                .build();
        final Imp secondImp = Imp.builder()
                .banner(Banner.builder().id("secondBanner").format(Collections.singletonList(bannerFormat)).build())
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpInmobi.of("plc"))))
                .build();

        final BidRequest bidRequest = BidRequest.builder()
                .imp(Arrays.asList(firstImp, secondImp))
                .build();
        // when
        final Result<List<HttpRequest<BidRequest>>> result = inmobiBidder.makeHttpRequests(bidRequest);

        // then
        final Banner firstExpectedBanner = Banner.builder()
                .id("firstBanner")
                .w(FORMAT_W)
                .h(FORMAT_H)
                .format(Collections.singletonList(bannerFormat))
                .build();
        final Banner secondExpectedBanner = Banner.builder()
                .id("secondBanner")
                .format(Collections.singletonList(bannerFormat))
                .build();
        assertThat(result.getErrors()).hasSize(0);
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .containsExactly(firstExpectedBanner, secondExpectedBanner);
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = inmobiBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors())
                .allMatch(error -> error.getMessage().startsWith("Failed to decode: Unrecognized token")
                        && error.getType().equals(BidderError.Type.bad_server_response));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = inmobiBidder.makeBids(httpCall, null);

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
        final Result<List<BidderBid>> result = inmobiBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfBannerIsPresentInRequestImp() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id(IMP_ID).banner(Banner.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid(IMP_ID))));

        // when
        final Result<List<BidderBid>> result = inmobiBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid(IMP_ID).build(), banner, null));
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfVideoIsPresentInRequestImp() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(BidRequest.builder()
                        .imp(singletonList(Imp.builder().id(IMP_ID).video(Video.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid(IMP_ID))));

        // when
        final Result<List<BidderBid>> result = inmobiBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid(IMP_ID).build(), video, null));
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfNativeIsPresentInRequestImp() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id(IMP_ID).xNative(Native.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid(IMP_ID))));

        // when
        final Result<List<BidderBid>> result = inmobiBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid(IMP_ID).build(), banner, null));
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder().bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
    }

    private static HttpCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return HttpCall.success(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
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

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                .id(IMP_ID)
                .banner(Banner.builder().id("bannerId").build())
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpInmobi.of("plc")))))
                .build();
    }
}
