package org.prebid.server.bidder.deepintent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
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
import org.prebid.server.proto.openrtb.ext.request.deepintent.ExtImpDeepintent;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;

public class DeepintentBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.com/prebid/bid";
    private static final String DISPLAY_MANAGER = "di_prebid";
    private static final String DISPLAY_MANAGER_VERSION = "2.0.0";
    private static final String IMP_EXT_TAG_ID = "testTagID";
    private static final String CURRENCY = "USD";

    private DeepintentBidder deepintentBidder;

    @Before
    public void setUp() {
        deepintentBidder = new DeepintentBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new DeepintentBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = deepintentBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_input);
    }

    @Test
    public void shouldSetDislplayManagerAndVersionAndTagIdToRequestImp() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = deepintentBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(0);
        final Imp expectedImp = Imp.builder()
                .displaymanager(DISPLAY_MANAGER)
                .displaymanagerver(DISPLAY_MANAGER_VERSION)
                .tagid(IMP_EXT_TAG_ID)
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpDeepintent.of(IMP_EXT_TAG_ID))))
                .build();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .containsExactly(expectedImp);
    }

    @Test
    public void makeRequestShouldCreateRequestForEveryValidImp() {
        // given
        final Imp firstImp = Imp.builder()
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpDeepintent.of(IMP_EXT_TAG_ID))))
                .build();
        final Imp secondImp = Imp.builder()
                .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))
                .build();
        final BidRequest bidRequest = BidRequest.builder()
                .imp(Arrays.asList(firstImp, secondImp))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = deepintentBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        final Imp expectedFirstImp = firstImp.toBuilder()
                .displaymanager(DISPLAY_MANAGER)
                .displaymanagerver(DISPLAY_MANAGER_VERSION)
                .tagid(IMP_EXT_TAG_ID)
                .build();

        assertThat(result.getValue())
                .hasSize(1)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .containsExactly(expectedFirstImp);
    }

    @Test
    public void makeRequestShouldCreateSeparateRequestForEveryImp() {
        // given
        final Imp firstImp = Imp.builder()
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpDeepintent.of("firstImpTagId"))))
                .build();
        final Imp secondImp = Imp.builder()
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpDeepintent.of("secondImpTagId"))))
                .build();
        final BidRequest bidRequest = BidRequest.builder()
                .imp(Arrays.asList(firstImp, secondImp))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = deepintentBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(0);
        final Imp expectedFirstImp = firstImp.toBuilder()
                .displaymanager(DISPLAY_MANAGER)
                .displaymanagerver(DISPLAY_MANAGER_VERSION)
                .tagid("firstImpTagId")
                .build();
        final Imp expectedSecondImp = secondImp.toBuilder()
                .displaymanager(DISPLAY_MANAGER)
                .displaymanagerver(DISPLAY_MANAGER_VERSION)
                .tagid("secondImpTagId")
                .build();

        assertThat(result.getValue())
                .hasSize(2)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .containsOnly(expectedFirstImp, expectedSecondImp);
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = deepintentBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Failed to decode: Unrecognized token");
        assertThat(result.getErrors())
                .extracting(BidderError::getType)
                .containsExactly(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = deepintentBidder.makeBids(httpCall, null);

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
        final Result<List<BidderBid>> result = deepintentBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
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
        final Result<List<BidderBid>> result = deepintentBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), banner, CURRENCY));
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfVideoIsPresentInRequestImp() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").video(Video.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = deepintentBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), video, CURRENCY));
    }

    @Test
    public void shouldReturnErrorIfBidImpIdNotFoundInImps() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").video(Video.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("notFoundId"))));

        // when
        final Result<List<BidderBid>> result = deepintentBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("Failed to find impression with id: notFoundId"));
    }

    @Test
    public void makeBidsShouldReturnBannerBidByDefault() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").xNative(Native.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = deepintentBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), banner, CURRENCY));
    }

    private static BidRequest givenBidRequest(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestCustomizer,
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                .imp(singletonList(givenImp(impCustomizer))))
                .build();
    }

    private static BidRequest givenBidRequest(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer);
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpDeepintent.of(IMP_EXT_TAG_ID)))))
                .build();
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .cur(CURRENCY)
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
}
