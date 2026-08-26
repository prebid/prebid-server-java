package org.prebid.server.bidder.adswag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Audio;
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
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.adswag.ExtImpAdswag;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

public class AdswagBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com/prebid/bid";
    private static final String SERVE_URL = "https://ads.adswag.ai/v1/ad?sc=c2ln&sig=YWJj";
    private static final String VAST_ADM = """
            <?xml version="1.0" encoding="UTF-8"?><VAST version="4.2">\
            <Ad id="req-abc"><Wrapper><AdSystem>Adswag</AdSystem>\
            <VASTAdTagURI><![CDATA[https://ads.adswag.ai/v1/vast?sc=c2ln&sig=YWJj]]></VASTAdTagURI>\
            </Wrapper></Ad></VAST>""";
    private static final String IFRAME_ADM = """
            <iframe src="https://ads.adswag.ai/v1/ad?sc=c2ln&amp;sig=YWJj" \
            width="300" height="250" frameborder="0" scrolling="no" marginheight="0" \
            marginwidth="0" style="border:0" title="Advertisement"></iframe>""";

    private final AdswagBidder target = new AdswagBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AdswagBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(
                imp -> imp.id("imp1").ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode())))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).startsWith("Error parsing imp.ext for impression imp1");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfPublisherIdIsMissing() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.id("imp1").ext(givenImpExt(" ", "placement"))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).isEqualTo("missing publisherId for imp imp1");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfNeitherSiteNorAppIsPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.id("imp1").ext(givenImpExt("pub-1", null))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).isEqualTo("request must contain either site or app");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldPromotePublisherIdAndRewriteImpExt() {
        // given
        final ObjectNode impExt = givenImpExt("pub-1", "plc-1");
        impExt.put("gpid", "/1111/homepage#div-1");
        final BidRequest bidRequest = givenBidRequest(
                request -> request.site(Site.builder()
                        .publisher(Publisher.builder().name("Example").build())
                        .build()),
                givenImp(imp -> imp.id("imp1").ext(impExt)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSite)
                .extracting(Site::getPublisher)
                .extracting(Publisher::getId, Publisher::getName)
                .containsExactly(tuple("pub-1", "Example"));
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .allSatisfy(ext -> {
                    assertThat(ext.has("bidder")).isFalse();
                    assertThat(ext.get("gpid").textValue()).isEqualTo("/1111/homepage#div-1");
                    assertThat(ext.get("adswag").get("placement_id").textValue()).isEqualTo("plc-1");
                });
    }

    @Test
    public void makeHttpRequestsShouldRemoveImpExtWhenOnlyBidderParamsPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.site(Site.builder().domain("example.nl").build()),
                givenImp(imp -> imp.id("imp1").ext(givenImpExt("pub-1", null))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .containsOnlyNulls();
    }

    @Test
    public void makeHttpRequestsShouldPromotePublisherIdToApp() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.app(App.builder().bundle("com.example.app").build()),
                givenImp(imp -> imp.id("imp1").ext(givenImpExt("pub-1", null))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getApp)
                .extracting(App::getPublisher)
                .extracting(Publisher::getId)
                .containsExactly("pub-1");
    }

    @Test
    public void makeHttpRequestsShouldDropImpsWithInvalidExtAndKeepValidOnes() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.site(Site.builder().domain("example.nl").build()),
                givenImp(imp -> imp.id("imp1").ext(givenImpExt("pub-1", null))),
                givenImp(imp -> imp.id("imp2").ext(givenImpExt(null, "plc-2"))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> assertThat(error.getMessage()).isEqualTo("missing publisherId for imp imp2"));
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getId)
                .containsExactly("imp1");
    }

    @Test
    public void makeHttpRequestsShouldSplitImpsByPublisherId() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                request -> request.site(Site.builder().domain("example.nl").build()),
                givenImp(imp -> imp.id("imp-a").ext(givenImpExt("pub-a", null))),
                givenImp(imp -> imp.id("imp-b").ext(givenImpExt("pub-b", null))),
                givenImp(imp -> imp.id("imp-a-2").ext(givenImpExt("pub-a", null))));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2);
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(payload -> payload.getSite().getPublisher().getId())
                .containsExactly("pub-a", "pub-b");
        assertThat(result.getValue())
                .extracting(HttpRequest::getPayload)
                .extracting(payload -> payload.getImp().stream().map(Imp::getId).toList())
                .containsExactly(List.of("imp-a", "imp-a-2"), List.of("imp-b"));
    }

    @Test
    public void makeBidsShouldReturnEmptyResultForEmptyBody() {
        // given
        final BidderCall<BidRequest> httpCall = BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().build(),
                HttpResponse.of(200, null, ""),
                null);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().build(),
                HttpResponse.of(200, null, "invalid_json"),
                null);

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
    public void makeBidsShouldSynthesizeIframeMarkupForBannerServeUrlBid() throws JsonProcessingException {
        // given
        final Bid responseBid = givenServeUrlBid("imp1");
        final BidRequest bidRequest = givenBidRequest(givenImp(imp -> imp.id("imp1").banner(givenBanner())));
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest, singletonList(responseBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType, BidderBid::getBidCurrency)
                .containsExactly(tuple(BidType.banner, "EUR"));
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getAdm, Bid::getW, Bid::getH, Bid::getMtype)
                .containsExactly(tuple(IFRAME_ADM, 300, 250, 1));
    }

    @Test
    public void makeBidsShouldPassThroughAdmWhenMarkupIsPresent() throws JsonProcessingException {
        // given
        final String loaderAdm = "<script src=\"https://ads.adswag.ai/v1/adj?sc=c2ln&sig=YWJj\"></script>";
        final Bid responseBid = givenServeUrlBid("imp1").toBuilder().adm(loaderAdm).build();
        final BidRequest bidRequest = givenBidRequest(givenImp(imp -> imp.id("imp1").banner(givenBanner())));
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest, singletonList(responseBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getAdm)
                .containsExactly(loaderAdm);
    }

    @Test
    public void makeBidsShouldReturnVideoBidForVastMarkup() throws JsonProcessingException {
        // given
        final Bid responseBid = givenVastBid("imp1");
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.id("imp1").video(Video.builder().w(640).h(360).build())));
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest, singletonList(responseBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.video);
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getAdm, Bid::getW, Bid::getH, Bid::getMtype)
                .containsExactly(tuple(VAST_ADM, 640, 360, 2));
    }

    @Test
    public void makeBidsShouldReturnAudioBidForVastMarkupOnAudioImp() throws JsonProcessingException {
        // given
        final Bid responseBid = givenVastBid("imp1");
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.id("imp1")
                        .banner(givenBanner())
                        .audio(Audio.builder().mimes(singletonList("audio/mpeg")).build())));
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest, singletonList(responseBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.audio);
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getMtype)
                .containsExactly(3);
    }

    @Test
    public void makeBidsShouldResolveServeUrlBidOnMultiFormatImpAsBanner() throws JsonProcessingException {
        // given
        final Bid responseBid = givenServeUrlBid("imp1");
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.id("imp1").banner(givenBanner()).video(Video.builder().w(640).h(360).build())));
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest, singletonList(responseBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.banner);
    }

    @Test
    public void makeBidsShouldSetNurlForVideoServeUrlBidWithoutAdm() throws JsonProcessingException {
        // given
        final Bid responseBid = givenServeUrlBid("imp1");
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.id("imp1").video(Video.builder().w(640).h(360).build())));
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest, singletonList(responseBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBid)
                .extracting(Bid::getNurl, Bid::getMtype)
                .containsExactly(tuple(SERVE_URL, 2));
    }

    @Test
    public void makeBidsShouldHonorMtypeWhenPresent() throws JsonProcessingException {
        // given
        final Bid responseBid = givenVastBid("imp1").toBuilder().mtype(2).build();
        final BidRequest bidRequest = givenBidRequest(
                givenImp(imp -> imp.id("imp1")
                        .audio(Audio.builder().mimes(singletonList("audio/mpeg")).build())
                        .video(Video.builder().w(640).h(360).build())));
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest, singletonList(responseBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getType)
                .containsExactly(BidType.video);
    }

    @Test
    public void makeBidsShouldReturnErrorForUnsupportedMtype() throws JsonProcessingException {
        // given
        final Bid responseBid = givenVastBid("imp1").toBuilder().mtype(4).build();
        final BidRequest bidRequest = givenBidRequest(givenImp(imp -> imp.id("imp1").banner(givenBanner())));
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest, singletonList(responseBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).isEqualTo("unsupported bid.mtype 4 for impression imp1");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorForUnknownImp() throws JsonProcessingException {
        // given
        final Bid responseBid = givenServeUrlBid("unknown-imp");
        final BidRequest bidRequest = givenBidRequest(givenImp(imp -> imp.id("imp1").banner(givenBanner())));
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest, singletonList(responseBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage())
                            .isEqualTo("bid adswag-unknown-imp references unknown imp unknown-imp");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorForBidWithoutAdmOrServeUrl() throws JsonProcessingException {
        // given
        final Bid responseBid = Bid.builder()
                .id("adswag-imp1")
                .impid("imp1")
                .price(BigDecimal.valueOf(2.5))
                .build();
        final BidRequest bidRequest = givenBidRequest(givenImp(imp -> imp.id("imp1").banner(givenBanner())));
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest, singletonList(responseBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage())
                            .isEqualTo("bid adswag-imp1 has neither adm nor ext.adswag.serve_url");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldDefaultCurrencyToEur() throws JsonProcessingException {
        // given
        final BidResponse bidResponse = BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder().bid(singletonList(givenServeUrlBid("imp1"))).build()))
                .build();
        final BidRequest bidRequest = givenBidRequest(givenImp(imp -> imp.id("imp1").banner(givenBanner())));
        final BidderCall<BidRequest> httpCall = BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, mapper.writeValueAsString(bidResponse)),
                null);

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(BidderBid::getBidCurrency)
                .containsExactly("EUR");
    }

    private static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
                                              Imp... imps) {

        return bidRequestCustomizer.apply(BidRequest.builder().imp(asList(imps))).build();
    }

    private static BidRequest givenBidRequest(Imp... imps) {
        return givenBidRequest(identity(), imps);
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()).build();
    }

    private static ObjectNode givenImpExt(String publisherId, String placementId) {
        return mapper.valueToTree(ExtPrebid.of(null, ExtImpAdswag.of(publisherId, placementId)));
    }

    private static Banner givenBanner() {
        return Banner.builder().format(singletonList(Format.builder().w(300).h(250).build())).build();
    }

    private static Bid givenServeUrlBid(String impId) {
        final ObjectNode bidExt = mapper.createObjectNode();
        bidExt.putObject("adswag").put("serve_url", SERVE_URL);
        return Bid.builder()
                .id("adswag-" + impId)
                .impid(impId)
                .price(BigDecimal.valueOf(2.5))
                .crid("cr-1")
                .ext(bidExt)
                .build();
    }

    private static Bid givenVastBid(String impId) {
        return Bid.builder()
                .id("adswag-" + impId)
                .impid(impId)
                .price(BigDecimal.valueOf(2.5))
                .crid("cr-1")
                .adm(VAST_ADM)
                .build();
    }

    private static BidResponse givenBidResponse(List<Bid> bids) {
        return BidResponse.builder()
                .cur("EUR")
                .seatbid(singletonList(SeatBid.builder().bid(bids).build()))
                .build();
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, List<Bid> bids)
            throws JsonProcessingException {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, mapper.writeValueAsString(givenBidResponse(bids))),
                null);
    }
}
