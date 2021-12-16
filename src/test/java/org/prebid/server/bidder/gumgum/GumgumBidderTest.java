package org.prebid.server.bidder.gumgum;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
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
import org.prebid.server.proto.openrtb.ext.request.gumgum.ExtImpGumgum;
import org.prebid.server.proto.openrtb.ext.request.gumgum.ExtImpGumgumBanner;
import org.prebid.server.proto.openrtb.ext.request.gumgum.ExtImpGumgumVideo;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;

public class GumgumBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com/providers/prbds2s/bid";

    private GumgumBidder gumgumBidder;

    @Before
    public void setUp() {
        gumgumBidder = new GumgumBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new GumgumBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorsIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder ->
                impBuilder.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = gumgumBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(2)
                .anySatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).isEqualTo("No valid impressions");
                })
                .anySatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).startsWith("Cannot deserialize value");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfNoValidImpressions() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(
                        givenImp(impBuilder -> impBuilder.video(Video.builder().build()))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = gumgumBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(2)
                .contains(BidderError.badInput("No valid impressions"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfVideoFieldsAreNotValid() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .video(Video.builder().w(0).build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpGumgum.of("zone", BigInteger.TEN, "irisId", null))))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = gumgumBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors())
                .containsExactlyInAnyOrder(BidderError.badInput("Invalid or missing video field(s)"),
                        BidderError.badInput("No valid impressions"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldModifyVideoExtOfIrisIdIsPresent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .video(Video.builder()
                                .w(20)
                                .h(30)
                                .maxduration(12)
                                .minduration(34)
                                .placement(33)
                                .linearity(233)
                                .build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpGumgum.of("zone", BigInteger.TEN, "irisId", null))))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = gumgumBidder.makeHttpRequests(bidRequest);

        // then
        final ObjectNode expectedVideoExt = mapper.valueToTree(ExtImpGumgumVideo.of("irisId"));
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getVideo)
                .extracting(Video::getExt)
                .containsExactly(expectedVideoExt);
    }

    @Test
    public void makeHttpRequestsShouldNotChangeBannerWidthAndHeightIfPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder ->
                impBuilder.banner(Banner.builder()
                        .format(singletonList(Format.builder().w(300).h(450).build()))
                        .w(600).h(900)
                        .build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = gumgumBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .extracting(Banner::getW, Banner::getH)
                .containsExactly(tuple(600, 900));
    }

    @Test
    public void makeHttpRequestsShouldUpdatePublisherIfPubIdIsPresent() {
        // given
        final Publisher sitePublisher = Publisher.builder().name("testPublisher").build();
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.site(Site.builder().publisher(sitePublisher).build()),
                impBuilder -> impBuilder.banner(Banner.builder().w(600).h(900).build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = gumgumBidder.makeHttpRequests(bidRequest);

        // then
        final Publisher expectedPublisher = sitePublisher.toBuilder().id("10").build();
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getSite)
                .extracting(Site::getPublisher)
                .containsExactly(expectedPublisher);
    }

    @Test
    public void makeHttpRequestsShouldSetBannerWidthAndHeightFromfirstFormatIfAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.banner(Banner.builder()
                        .format(singletonList(Format.builder().w(300).h(450).build()))
                        .build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = gumgumBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .extracting(Banner::getW, Banner::getH)
                .containsExactly(tuple(300, 450));
    }

    @Test
    public void makeHttpRequestsShouldNotMakeSiteIfAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = gumgumBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSite)
                .containsNull();
    }

    @Test
    public void makeHttpRequestsShouldSetSiteIdFromLastValidImpExtZone() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().build())
                .imp(asList(
                        givenImp(impBuilder -> impBuilder
                                .banner(Banner.builder().build())
                                .ext(mapper.valueToTree(ExtPrebid.of(null,
                                        ExtImpGumgum.of("ignored zone", BigInteger.TEN, "irisId", null))))),
                        givenImp(identity())))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = gumgumBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSite)
                .extracting(Site::getId)
                .containsExactly("zone");
    }

    @Test
    public void makeHttpRequestsShouldNotModifyBannerExtIfSlotIsZeroOrNull() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().build())
                .imp(asList(
                        givenImp(impBuilder -> impBuilder
                                .id("123")
                                .banner(Banner.builder()
                                        .format(singletonList(Format.builder().w(1).h(1).build()))
                                        .build())
                                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpGumgum.of("ignored zone",
                                        BigInteger.TEN, "irisId", 0L))))),
                        givenImp(impBuilder -> impBuilder
                                .id("345")
                                .banner(Banner.builder()
                                        .format(singletonList(Format.builder().w(1).h(1).build()))
                                        .build())
                                .ext(mapper.valueToTree(ExtPrebid.of(null,
                                        ExtImpGumgum.of("ignored zone", BigInteger.TEN, "irisId", null))))
                        )))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = gumgumBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .extracting(Banner::getExt)
                .containsExactly(null, null);
    }

    @Test
    public void makeHttpRequestsShouldSetBannerExtWithBiggestBannerFormatIfSlotIsNotZero() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .id("123")
                .banner(Banner.builder()
                        .format(asList(
                                Format.builder().w(120).h(80).build(),
                                Format.builder().w(120).h(100).build(),
                                Format.builder().w(100).h(100).build()))
                        .build())
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpGumgum.of("ignored zone",
                        BigInteger.TEN, "irisId", 42L)))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = gumgumBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .extracting(Banner::getExt)
                .containsExactly(mapper.valueToTree(ExtImpGumgumBanner.of(42L, 120, 100)));
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = gumgumBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = gumgumBidder.makeBids(httpCall, null);

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
        final Result<List<BidderBid>> result = gumgumBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnVideoBid() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().id("123").build()))
                .build();
        final HttpCall<BidRequest> httpCall = givenHttpCall(bidRequest, mapper.writeValueAsString(
                givenBidResponse(bidBuilder -> bidBuilder
                        .impid("123")
                        .adm("<?xml version=\"1.0\" ${AUCTION_PRICE} xml>")
                        .price(BigDecimal.valueOf(10)))));

        // when
        final Result<List<BidderBid>> result = gumgumBidder.makeBids(httpCall, bidRequest);

        // then
        final Bid expectedBid = Bid.builder()
                .impid("123")
                .adm("<?xml version=\"1.0\" 10 xml>")
                .price(BigDecimal.valueOf(10))
                .build();
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(expectedBid, video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnBannerBid() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().banner(Banner.builder().build()).id("123").build()))
                .build();
        final HttpCall<BidRequest> httpCall = givenHttpCall(bidRequest, mapper.writeValueAsString(
                givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = gumgumBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldTolerateWithNullSeatOrBidValues() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().banner(Banner.builder().build()).id("123").build()))
                .build();

        final BidResponse bidResponse = BidResponse.builder()
                .cur("USD")
                .seatbid(asList(SeatBid.builder()
                        .bid(asList(Bid.builder().id("123").build(), null))
                        .build(), null))
                .build();

        final HttpCall<BidRequest> httpCall = givenHttpCall(bidRequest, mapper.writeValueAsString(bidResponse));

        // when
        final Result<List<BidderBid>> result = gumgumBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
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
                                ExtImpGumgum.of("zone", BigInteger.TEN, "irisId", 1L)))))
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
