package org.prebid.server.bidder.richaudience;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.MultiMap;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.richaudience.ExtImpRichaudience;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;

public class RichaudienceBidderTest extends VertxTest {

    private final RichaudienceBidder target = new RichaudienceBidder("https://test.domain.dm/uri", jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new RichaudienceBidder("incorrect_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfDeviceIsAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(request -> request.device(null));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1).containsExactly(BidderError.badInput("Device IP is required."));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfDeviceIpIsAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(request -> request.device(Device.builder().build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1).containsExactly(BidderError.badInput("Device IP is required."));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfSomeImpBannerIsInvalid() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(impBuilder -> impBuilder.id("imp_id1").banner(Banner.builder().w(100).h(200).build())),
                givenImp(impBuilder -> impBuilder.id("imp_id2").banner(Banner.builder().build())));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(request -> request.getPayload().getImp().get(0).getId(), HttpRequest::getImpIds)
                .containsExactly(tuple("imp_id1", Set.of("imp_id1")));
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.badInput("Banner W/H/Format is required. ImpId: imp_id2"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfSomeImpVideoIsInvalid() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(impBuilder -> impBuilder.id("imp_id1").video(Video.builder().w(100).h(200).build())),
                givenImp(impBuilder -> impBuilder.id("imp_id2").video(Video.builder().build())));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(request -> request.getPayload().getImp().get(0).getId(), HttpRequest::getImpIds)
                .containsExactly(tuple("imp_id1", Set.of("imp_id1")));
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(BidderError.badInput("Video W and H are required. ImpId: imp_id2"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfSomeImpsAreInvalid() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(impBuilder -> impBuilder.id("imp_id1").video(Video.builder().w(100).h(200).build())),
                givenImp(impBuilder -> impBuilder.id("imp_id2").video(Video.builder().build())),
                givenImp(impBuilder -> impBuilder.id("imp_id3").audio(Audio.builder().build())),
                givenImp(impBuilder -> impBuilder.id("imp_id4").banner(Banner.builder().w(100).h(200).build())),
                givenImp(impBuilder -> impBuilder.id("imp_id5").banner(Banner.builder().build())),
                givenImp(impBuilder -> impBuilder.id("imp_id6").xNative(Native.builder().build())));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(4)
                .extracting(request -> request.getPayload().getImp().get(0).getId(), HttpRequest::getImpIds)
                .containsExactly(
                        tuple("imp_id1", Set.of("imp_id1")),
                        tuple("imp_id3", Set.of("imp_id3")),
                        tuple("imp_id4", Set.of("imp_id4")),
                        tuple("imp_id6", Set.of("imp_id6")));
        assertThat(result.getErrors()).hasSize(2)
                .containsExactly(
                        BidderError.badInput("Video W and H are required. ImpId: imp_id2"),
                        BidderError.badInput("Banner W/H/Format is required. ImpId: imp_id5"));
    }

    @Test
    public void makeHttpRequestsShouldSetImpSecureToFalseIfSitePageIsInsecure() {
        // given
        final BidRequest bidRequest = givenBidRequest("http://insecure.site.st/uri",
                givenImp(identity()), givenImp(identity()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(2)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .hasSize(2)
                .extracting(Imp::getSecure)
                .allMatch(Integer.valueOf(0)::equals);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldSetImpSecureToTrueIfSitePageIsSecure() {
        // given
        final BidRequest bidRequest = givenBidRequest("https://secure.site.st/uri",
                givenImp(identity()), givenImp(identity()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(2)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .hasSize(2)
                .extracting(Imp::getSecure)
                .allMatch(Integer.valueOf(1)::equals);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldSetImpTagIdIfImpExtPidIsPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(identity()),
                givenImp(ext -> ext.pid("imp_id"), identity()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(2)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .hasSize(2)
                .extracting(Imp::getTagid)
                .containsExactly(null, "imp_id");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldSetRequestTestImpExtTestIsPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(ext -> ext.test(true), identity()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getTest)
                .containsExactly(1);
    }

    @Test
    public void makeHttpRequestsShouldSetRequestDeviceIpImpExtTestIsPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(ext -> ext.test(true), identity()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getDevice)
                .extracting(Device::getIp)
                .containsExactly("11.222.33.44");
    }

    @Test
    public void makeHttpRequestsShouldHaveCorrectHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(identity()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getHeaders)
                .flatExtracting(MultiMap::entries)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()),
                        tuple(HttpUtil.X_OPENRTB_VERSION_HEADER.toString(), "2.5")
                );
    }

    @Test
    public void makeHttpRequestsShouldSetCorrectImpBidFloorCur() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(identity()),
                givenImp(imp -> imp.bidfloorcur("RUB")),
                givenImp(ext -> ext.bidFloorCur("UAH"), identity()),
                givenImp(ext -> ext.bidFloorCur("BYN"), imp -> imp.bidfloorcur("RUB")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(4)
                .extracting(HttpRequest::getPayload)
                .flatExtracting(BidRequest::getImp)
                .hasSize(4)
                .extracting(Imp::getBidfloorcur)
                .containsExactly("USD", "RUB", "UAH", "BYN");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldSetTestToTrueIfSomeImpExtTestIsTrue() {
        // given
        final BidRequest bidRequest = givenBidRequest(givenImp(ext -> ext.test(true), identity()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getTest)
                .containsExactly(1);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldSetSiteDomainIfItIsAbsentAndSitePageIsPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest("https://domain.dm/uri", givenImp(identity()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSite)
                .extracting(Site::getDomain)
                .containsExactly("domain.dm");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldSetSiteKeywordsAsImpTagIdWhenSiteIsPresent() {
        // given
        final Imp imp = givenImp(impBuilder -> impBuilder.id("imp_id").tagid("tag_id"));
        final BidRequest bidRequest = givenBidRequest(imp)
                .toBuilder()
                .site(Site.builder().build())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getSite)
                .extracting(Site::getKeywords)
                .containsExactly("tagId=tag_id");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldSetAppKeywordsAsImpTagIdWhenAppIsPresent() {
        // given
        final Imp imp = givenImp(impBuilder -> impBuilder.id("imp_id").tagid("tag_id"));
        final BidRequest bidRequest = givenBidRequest(imp)
                .toBuilder()
                .app(App.builder().build())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .extracting(BidRequest::getApp)
                .extracting(App::getKeywords)
                .containsExactly("tagId=tag_id");
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("Incorrect body");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors())
                .hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token");
                });
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("null");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("{}");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerIfPresentInImp() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(
                givenImp(impBuilder -> impBuilder.id("imp_id").banner(Banner.builder().build())));
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("imp_id"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("imp_id").build(), banner, null));
    }

    @Test
    public void makeBidsShouldReturnVideoTypeIfPresentInImp() throws JsonProcessingException {
        // given
        final Video video = Video.builder().w(100).h(100).build();
        final BidRequest bidRequest = givenBidRequest(givenImp(impBuilder -> impBuilder.id("imp_id").video(video)));
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                bidRequest,
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("imp_id"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(
                BidderBid.of(Bid.builder().w(100).h(100).impid("imp_id").build(), BidType.video, null));
    }

    @Test
    public void makeBidsShouldReturnDefaultBannerTypeIfNotPresentAnyTypeInImp() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(Imp.builder().id("imp_id").build());
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest,
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("imp_id"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("imp_id").build(), banner, null));
    }

    @Test
    public void makeBidsShouldReturnImpWithoutImpsWithUnknownIds() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(Imp.builder().id("imp_id").build());
        final BidderCall<BidRequest> httpCall = givenHttpCall(bidRequest,
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("unknown_imp_id"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    private BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer) {
        final BidRequest.BidRequestBuilder builder = BidRequest.builder()
                .device(Device.builder().ip("Some ip.").build());
        return bidRequestCustomizer.apply(builder).build();
    }

    private BidRequest givenBidRequest(Imp... imps) {
        return givenBidRequest(request -> request.imp(List.of(imps)));
    }

    private BidRequest givenBidRequest(String url, Imp... imps) {
        return givenBidRequest(request -> request.site(Site.builder().page(url).build()).imp(List.of(imps)));
    }

    private Imp givenImp(UnaryOperator<ExtImpRichaudience.ExtImpRichaudienceBuilder> extCustomizer,
                         UnaryOperator<Imp.ImpBuilder> impCustomizer) {

        final ExtImpRichaudience extImpRichaudience = extCustomizer.apply(ExtImpRichaudience.builder()).build();
        final ObjectNode ext = mapper.valueToTree(ExtPrebid.of(null, extImpRichaudience));
        final Imp.ImpBuilder builder = Imp.builder()
                .id("imp_id")
                .banner(Banner.builder().w(21).h(9).build())
                .ext(ext);

        return impCustomizer.apply(builder).build();
    }

    private Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return givenImp(identity(), impCustomizer);
    }

    private BidderCall<BidRequest> givenHttpCall(String body) {
        final HttpResponse response = HttpResponse.of(200, null, body);
        return BidderCall.succeededHttp(null, response, null);
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }

    private static BidResponse givenBidResponse(UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder().bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
    }
}
