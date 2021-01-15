package org.prebid.server.bidder.smartyads;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
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
import org.prebid.server.proto.openrtb.ext.request.smartyads.ExtImpSmartyAds;
import org.prebid.server.util.HttpUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

public class SmartyAdsBidderTest extends VertxTest {

    private static final String ENDPOINT_URL =
            "http://{{Host}}.test.com/bid?param1={{SourceId}}&param2={{AccountID}}";

    private SmartyAdsBidder smartyAdsBidder;

    @Before
    public void setUp() {
        smartyAdsBidder = new SmartyAdsBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SmartyAdsBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = smartyAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("ext.bidder not provided");
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfExtBidderAccountIdParamIsMissed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpSmartyAds.of("", "testSourceId", "testHost")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = smartyAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors())
                .containsExactly(BidderError.badInput("accountId is a required ext.bidder param"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfExtBidderSourceIdParamIsMissed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpSmartyAds.of("testAccountId", "", "testHost")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = smartyAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).containsExactly(BidderError.badInput("sourceId is a required ext.bidder param"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfExtBidderHostParamIsMissed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpSmartyAds.of("testAccountId", "testSourceId", "")))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = smartyAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).containsExactly(BidderError.badInput("host is a required ext.bidder param"));
    }

    @Test
    public void makeHttpRequestsShouldCreateCorrectURL() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder);

        // when
        final Result<List<HttpRequest<BidRequest>>> result = smartyAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().get(0).getUri())
                .isEqualTo("http://testHost.test.com/bid?param1=testSourceId&param2=testAccountId");
    }

    @Test
    public void shouldRemoveAllImpsExts() {
        // given
        final Imp firstImp = givenImp(impBuilder -> impBuilder.id("346"));
        final Imp secondImp = givenImp(impBuilder -> impBuilder.id("789"));

        final BidRequest bidRequest = BidRequest.builder().imp(Arrays.asList(firstImp, secondImp)).build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = smartyAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .containsOnly(updateImpExtWithNull(firstImp), updateImpExtWithNull(secondImp));
    }

    private Imp updateImpExtWithNull(Imp imp) {
        return imp.toBuilder().ext(null).build();
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = smartyAdsBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).containsExactly(BidderError.badServerResponse("Bad Server Response"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = smartyAdsBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).containsExactly(BidderError.badServerResponse("Bad Server Response"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = smartyAdsBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).containsExactly(BidderError.badServerResponse("Empty SeatBid array"));
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
        final Result<List<BidderBid>> result = smartyAdsBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnEmptyValueIfBidsAreNotPresent() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").banner(Banner.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(BidResponse.builder()
                        .seatbid(singletonList(SeatBid.builder()
                                .bid(null)
                                .build()))
                        .build()));

        // when
        final Result<List<BidderBid>> result = smartyAdsBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldWorkWithOnlyFirstSeatBid() throws JsonProcessingException {
        // given
        final SeatBid firstSeat = SeatBid.builder()
                .bid(Collections.singletonList(Bid.builder().impid("456").build()))
                .build();

        final SeatBid secondSeat = SeatBid.builder()
                .bid(Collections.singletonList(Bid.builder().impid("789").build()))
                .build();

        final BidResponse bidResponse = BidResponse.builder()
                .cur("USD")
                .seatbid(Arrays.asList(firstSeat, secondSeat))
                .build();

        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").banner(Banner.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(bidResponse));

        // when
        final Result<List<BidderBid>> result = smartyAdsBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("456").build(), banner, "USD"));
    }

    @Test
    public void makeHttpRequestsShouldSetAdditionalHeadersIfDeviceFieldsAreNotEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                requestBuilder -> requestBuilder
                        .device(Device.builder()
                                .ua("user_agent")
                                .ip("test_ip")
                                .dnt(23)
                                .language("testLang")
                                .build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = smartyAdsBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getHeaders)
                .flatExtracting(MultiMap::entries)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), "application/json;charset=utf-8"),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), "application/json"),
                        tuple(HttpUtil.ACCEPT_LANGUAGE_HEADER.toString(), "testLang"),
                        tuple(HttpUtil.DNT_HEADER.toString(), "23"),
                        tuple(HttpUtil.USER_AGENT_HEADER.toString(), "user_agent"),
                        tuple(HttpUtil.X_OPENRTB_VERSION_HEADER.toString(), "2.5"),
                        tuple(HttpUtil.X_FORWARDED_FOR_HEADER.toString(), "test_ip"));
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
        final Result<List<BidderBid>> result = smartyAdsBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnNativeBidIfNativeIsPresentInRequestImp() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").xNative(Native.builder().build()).build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = smartyAdsBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), xNative, "USD"));
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
                .id("123")
                .banner(Banner.builder().id("banner_id").build())
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpSmartyAds.of("testAccountId", "testSourceId", "testHost")))))
                .build();
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
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
