package org.prebid.server.bidder.taboola;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.taboola.ExtImpTaboola;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

public class TaboolaBidderTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private TaboolaBidder taboolaBidder;

    @Before
    public void setUp() {
        taboolaBidder =
                new TaboolaBidder("https://{{MediaType}}.bidder.taboola.com/OpenRTB/PS/auction/{{Host}}/{{PublisherID}}",
                        "http://localhost-test.com",
                        jacksonMapper);
    }

    @Test
    public void createBidderWithWrongEndpointShouldThrowException() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TaboolaBidder("incorrect.endpoint",
                "http://localhost:8090",
                jacksonMapper));
    }

    @Test
    public void makeHttpRequestWithInvalidTypeShouldReturnEmptyResponse() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestCustomizer -> bidRequestCustomizer,
                givenImp(impCustomizer -> impCustomizer.video(Video.builder().build())));

        // when
        Result<List<HttpRequest<BidRequest>>> result = taboolaBidder.makeHttpRequests(bidRequest);
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestWithInvalidImpExtShouldReturnBidderError() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestCustomizer -> bidRequestCustomizer,
                givenImp(impCustomizer -> impCustomizer.banner(Banner.builder().build())
                        .ext(mapper
                                .valueToTree(ExtPrebid.of(null, "invalid")))));

        // when
        Result<List<HttpRequest<BidRequest>>> result = taboolaBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnResultWhereUseValuesFromBigRequestWithoutChangeWhenImpExtIsFullEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestCustomizer -> bidRequestCustomizer
                        .bcat(List.of("test-bCat"))
                        .badv(List.of("test-bAdv")),
                givenImp(impCustomizer -> impCustomizer
                                .banner(Banner.builder().build())
                                .tagid("imp-tag-id")
                                .bidfloor(BigDecimal.TEN),
                        extImpCustomizer -> extImpCustomizer));

        // when
        Result<List<HttpRequest<BidRequest>>> result = taboolaBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://display.bidder.taboola.com/OpenRTB/PS/auction/localhost-test.com/");
        // and imp not modified
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor, Imp::getTagid)
                .containsExactly(tuple(BigDecimal.TEN, "imp-tag-id"));
        // and bid request not modified
        assertThat(result.getValue())
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getBcat, BidRequest::getBadv)
                .containsExactly(tuple(List.of("test-bCat"), List.of("test-bAdv")));
    }

    @Test
    public void makeHttpRequestShouldContainProperUriAndHeaderWhenAllDataPresentInRequestAndTypeIsBanner() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestCustomizer -> bidRequestCustomizer,
                givenImp(impCustomizer -> impCustomizer.banner(Banner.builder().build())));

        // when
        Result<List<HttpRequest<BidRequest>>> result = taboolaBidder.makeHttpRequests(bidRequest);
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://display.bidder.taboola.com/OpenRTB/PS/auction/localhost-test.com/publisherId");
    }

    @Test
    public void makeHttpRequestShouldContainProperUriAndHeaderWhenAllDataPresentInRequestAndTypeIsNative() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestCustomizer -> bidRequestCustomizer,
                givenImp(impCustomizer -> impCustomizer.xNative(Native.builder().build())));

        // when
        Result<List<HttpRequest<BidRequest>>> result = taboolaBidder.makeHttpRequests(bidRequest);
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://native.bidder.taboola.com/OpenRTB/PS/auction/localhost-test.com/publisherId");
    }

    @Test
    public void makeHttpRequestWitSiteAndMinimalImpExpDataShouldUpdateInitialSiteRequest() {
        // given
        final Imp impBanner = givenImp(
                impCustomizer ->
                        impCustomizer.banner(Banner.builder().build()),
                extImpCustomizer -> extImpCustomizer.tagId("tagId").publisherId("publisherId")
        );

        final Site requestSite = Site.builder()
                .id("siteId")
                .name("siteName")
                .domain("site.com")
                .publisher(Publisher.builder().id("site_publisher_id").build())
                .build();

        final BidRequest bidRequest = BidRequest
                .builder().imp(List.of(impBanner)).site(requestSite).build();

        // when
        Result<List<HttpRequest<BidRequest>>> result = taboolaBidder.makeHttpRequests(bidRequest);

        // then
        final Site expectedSite = Site.builder()
                .id("publisherId")
                .name("publisherId")
                .domain("site.com")
                .publisher(Publisher.builder().id("publisherId").build())
                .build();
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getSite)
                .containsExactly(expectedSite);
    }

    @Test
    public void makeHttpRequestWithEmptySiteShouldTakeDataForSiteFromImpValues() {
        // given
        final Imp impBanner = givenImp(
                impCustomizer ->
                        impCustomizer.banner(Banner.builder().build()),
                extImpCustomizer -> extImpCustomizer
                        .publisherId("token")
                        .publisherDomain("test.com")
                        .tagId("1")
                        .bidFloor(BigDecimal.TEN)
                        .bCat(List.of("test-cat"))
                        .bAdv(List.of("test-badv"))
                        .pageType("test")
                        .position(1)
        );

        final BidRequest bidRequest = BidRequest
                .builder().imp(List.of(impBanner)).site(null).build();

        // when
        Result<List<HttpRequest<BidRequest>>> result = taboolaBidder.makeHttpRequests(bidRequest);

        // then
        final Site expectedSite = Site.builder()
                .id("token")
                .name("token")
                .domain("test.com")
                .publisher(Publisher.builder().id("token").build())
                .build();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getSite)
                .containsExactly(expectedSite);
    }

    @Test
    public void makeHttpRequestShouldSplitInTwoSeparateRequests() {
        // given
        final Imp impBanner = givenImp(impCustomizer ->
                impCustomizer.banner(Banner.builder().build()), extImpCustomizer -> extImpCustomizer
                .publisherId("publisher_banner_Id")
                .bidFloor(BigDecimal.TEN));
        final Imp impNative = givenImp(impCustomizer ->
                impCustomizer.xNative(Native.builder().build()), extImpCustomizer -> extImpCustomizer
                .publisherId("publisher_native_Id")
                .bidFloor(BigDecimal.ONE));
        final BidRequest bidRequest = BidRequest.builder().imp(List.of(impBanner, impNative)).build();

        // when
        Result<List<HttpRequest<BidRequest>>> result = taboolaBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBidfloor)
                .containsExactlyInAnyOrder(BigDecimal.ONE, BigDecimal.TEN);
    }

    @Test
    public void makeBidsWithInvalidBodyShouldResultInError() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = taboolaBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Failed to decode: Unrecognized token");
    }

    @Test
    public void makeBidsShouldReturnBidderErrorWhenImpIsInvalidBidderType() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestCustomizer -> bidRequestCustomizer,
                givenImp(impCustomizer -> impCustomizer
                        .id("123")
                        .video(Video.builder().build())));
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(identity())));

        // when
        final Result<List<BidderBid>> result = taboolaBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .extracting(BidderError::getMessage)
                .containsOnly("Failed to find banner/native impression \"123\"");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsReturnEmptyListsResultWhenEmptySeatBidInBidResponse() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = taboolaBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnValidBidResponseWithBannerTypeWhenImpIncludeNativeAndBanner()
            throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestCustomizer -> bidRequestCustomizer,
                givenImp(impCustomizer -> impCustomizer
                        .id("123")
                        .xNative(Native.builder().build())
                        .banner(Banner.builder().build())));
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(identity())));

        // when
        final Result<List<BidderBid>> result = taboolaBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(BidderBid::getType)
                .containsExactly(BidType.banner);
    }

    @Test
    public void makeBidsShouldReturnValidBidResponseWithBannerWhenRequestHaveTwoImp() throws JsonProcessingException {
        // given
        final Imp impBanner = givenImp(impCustomizer ->
                        impCustomizer.id("123").banner(Banner.builder().build()),
                extImpCustomizer -> extImpCustomizer
                        .publisherId("publisher_video_Id")
                        .bidFloor(BigDecimal.TEN)
                        .publisherDomain("test.com")
                        .tagId("tag_video"));
        final Imp impNative = givenImp(impCustomizer ->
                        impCustomizer.id("124").xNative(Native.builder().build()),
                extImpCustomizer -> extImpCustomizer
                        .publisherId("publisher_native_Id")
                        .bidFloor(BigDecimal.TEN)
                        .publisherDomain("test.com")
                        .tagId("tag_native"));
        final BidRequest bidRequest = BidRequest.builder().imp(List.of(impBanner, impNative)).build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(identity())));

        // when
        final Result<List<BidderBid>> result = taboolaBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(BidderBid::getType)
                .containsExactly(BidType.banner);
    }

    private static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
                                              Imp... imps) {

        return bidRequestCustomizer.apply(BidRequest.builder().imp(List.of(imps))).build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return givenImp(impCustomizer, extImpCustomizer -> extImpCustomizer
                .publisherId("publisherId")
                .publisherDomain("test.com")
                .bidFloor(BigDecimal.TEN)
                .tagId("tagId")
                .position(1));
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer,
                                UnaryOperator<ExtImpTaboola.ExtImpTaboolaBuilder> extImpCustomizer) {
        return impCustomizer
                .apply(Imp.builder().ext(mapper
                        .valueToTree(ExtPrebid.of(null, extImpCustomizer.apply(ExtImpTaboola.builder()).build()))))
                .build();
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }

    private static BidResponse givenBidResponse(UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .seatbid(List.of(SeatBid.builder()
                        .bid(List.of(bidCustomizer.apply(Bid.builder().impid("123")).build()))
                        .build()))
                .build();
    }
}
