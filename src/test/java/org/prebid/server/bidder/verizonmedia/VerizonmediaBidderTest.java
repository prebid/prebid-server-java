package org.prebid.server.bidder.verizonmedia;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
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
import org.prebid.server.proto.openrtb.ext.request.verizonmedia.ExtImpVerizonmedia;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;

public class VerizonmediaBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";

    private VerizonmediaBidder verizonmediaBidder;

    @Before
    public void setUp() {
        verizonmediaBidder = new VerizonmediaBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new VerizonmediaBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = verizonmediaBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("imp #0: Cannot deserialize instance");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenDcnIsEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpVerizonmedia.of("", null)))),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = verizonmediaBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("imp #0: missing param dcn"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenPosIsEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpVerizonmedia.of("dcn", "")))),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = verizonmediaBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("imp #0: missing param pos"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldCreateARequestForEachImpAndSkipImpsWithErrors() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(
                        givenImp(impBuilder -> impBuilder.id("imp1")),
                        givenImp(impBuilder -> impBuilder.id("imp2")
                                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpVerizonmedia.of("dcn", ""))))),
                        givenImp(impBuilder -> impBuilder.id("imp3"))))
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = verizonmediaBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("imp #1: missing param pos"));
        assertThat(result.getValue()).hasSize(2)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).hasSize(2)
                .extracting(Imp::getId)
                .containsOnly("imp1", "imp3");
    }

    @Test
    public void makeHttpRequestsShouldAlwaysSetSiteIdAndImpTagIdFromImpExt() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(), identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = verizonmediaBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getSite)
                .extracting(Site::getId)
                .containsOnly("dcn");
        assertThat(result.getValue())
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp).hasSize(1)
                .extracting(Imp::getTagid)
                .containsOnly("pos");
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenBannerWidthIsZero() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.banner(Banner.builder().w(0).h(100).build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = verizonmediaBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("Invalid sizes provided for Banner 0x100"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenBannerHeightIsZero() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.banner(Banner.builder().w(100).h(0).build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = verizonmediaBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("Invalid sizes provided for Banner 100x0"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenBannerHasNoFormats() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.banner(Banner.builder().build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = verizonmediaBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1).containsOnly(BidderError.badInput("No sizes provided for Banner"));
    }

    @Test
    public void makeHttpRequestsSetFirstImpressionBannerWidthAndHeightWhenFromFirstFormatIfTheyAreNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .banner(Banner.builder().format(singletonList(Format.builder().w(250).h(300).build())).build()),
                identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = verizonmediaBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .extracting(Banner::getW, Banner::getH)
                .containsOnly(tuple(250, 300));
    }

    @Test
    public void makeHttpRequestsShouldSetExpectedHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity(),
                requestBuilder -> requestBuilder.site(null).device(Device.builder().ua("UA").build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = verizonmediaBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue().get(0).getHeaders())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(tuple("User-Agent", "UA"),
                        tuple("x-openrtb-version", "2.5"),
                        tuple("Content-Type", "application/json;charset=utf-8"),
                        tuple("Accept", "application/json"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = verizonmediaBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Failed to decode: Unrecognized token");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = verizonmediaBidder.makeBids(httpCall, null);

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
        final Result<List<BidderBid>> result = verizonmediaBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfBidResponseSeatBidIsEmpty() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(BidResponse.builder().seatbid(emptyList()).build()));

        // when
        final Result<List<BidderBid>> result = verizonmediaBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badServerResponse("Invalid SeatBids count: 0"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorWhenBidImpIdIsNotPresent() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(singletonList(Imp.builder()
                                .banner(Banner.builder().build())
                                .id("123").build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("321"))));

        // when
        final Result<List<BidderBid>> result = verizonmediaBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badServerResponse("Unknown ad unit code '321'"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldSkipNotBannerImpAndReturnBannerBidWhenBannerPresent() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                BidRequest.builder()
                        .imp(asList(Imp.builder().id("123").build(),
                                Imp.builder().banner(Banner.builder().build()).id("321").build()))
                        .build(),
                mapper.writeValueAsString(BidResponse.builder()
                        .seatbid(singletonList(SeatBid.builder()
                                .bid(asList(Bid.builder().impid("123").build(),
                                        Bid.builder().impid("321").build()))
                                .build()))
                        .build()));

        // when
        final Result<List<BidderBid>> result = verizonmediaBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("321").build(), banner, "USD"));
    }

    @Test
    public void extractTargetingShouldReturnEmptyMap() {
        assertThat(verizonmediaBidder.extractTargeting(mapper.createObjectNode())).isEqualTo(emptyMap());
    }

    private static BidRequest givenBidRequest(
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer,
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> requestCustomizer) {
        return requestCustomizer.apply(BidRequest.builder()
                .site(Site.builder().id("123").build())
                .imp(singletonList(givenImp(impCustomizer))))
                .build();
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                .tagid("tagId")
                .banner(Banner.builder().w(100).h(100).build())
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpVerizonmedia.of("dcn", "pos")))))
                .build();
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
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
