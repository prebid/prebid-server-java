package org.prebid.server.bidder.smaato;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
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
import org.prebid.server.bidder.smaato.proto.SmaatoSiteExtData;
import org.prebid.server.bidder.smaato.proto.SmaatoUserExtData;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.smaato.ExtImpSmaato;

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

public class SmaatoBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";

    private SmaatoBidder smaatoBidder;

    @Before
    public void setUp() {
        smaatoBidder = new SmaatoBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SmaatoBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .id("123")
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = smaatoBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Cannot deserialize instance");
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfBannerOrVideoImpIsEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.id("impid").banner(null).video(null));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = smaatoBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError
                        .badInput("invalid MediaType. SMAATO only supports Banner and Video. Ignoring ImpID=impid"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfBannerSizeIsEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder.id("impid").banner(Banner.builder().build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = smaatoBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError
                        .badInput("No sizes provided for Banner null"));
    }

    @Test
    public void makeHttpRequestsShouldNotChangeBannerWidthAndHeightIfPresent() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .banner(Banner.builder()
                                .format(singletonList(Format.builder().w(300).h(500).build()))
                                .w(200)
                                .h(150)
                                .build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = smaatoBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .extracting(Banner::getW, Banner::getH)
                .containsOnly(tuple(200, 150));
    }

    @Test
    public void makeHttpRequestsShouldSetBannerWidthAndHeightFromFirstFormatIfEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .banner(Banner.builder()
                                .format(asList(Format.builder().w(300).h(500).build(),
                                        Format.builder().w(450).h(150).build()))
                                .build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = smaatoBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getBanner)
                .extracting(Banner::getW, Banner::getH)
                .containsOnly(tuple(300, 500));
    }

    @Test
    public void makeHttpRequestsShouldModifyRequestSite() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("123")
                        .banner(Banner.builder()
                                .id("banner_id").format(asList(Format.builder().w(300).h(500).build(),
                                        Format.builder().w(450).h(150).build())).build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpSmaato.of("publisherId", "adspaceId")))).build()))
                .site(Site.builder()
                        .ext(ExtSite.of(null, mapper.valueToTree(SmaatoSiteExtData.of("keywords"))))
                        .build())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = smaatoBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getSite)
                .extracting(Site::getPublisher)
                .extracting(Publisher::getId)
                .containsOnly("publisherId");
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getSite)
                .extracting(Site::getKeywords)
                .containsOnly("keywords");
    }

    @Test
    public void makeHttpRequestsShouldModifyRequestUser() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("123")
                        .banner(Banner.builder()
                                .id("banner_id").format(asList(Format.builder().w(300).h(500)
                                                .build(),
                                        Format.builder().w(450).h(150).build())).build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpSmaato.of("publisherId", "adspaceId")))).build()))
                .user(User.builder()
                        .ext(ExtUser.builder()
                                .data(mapper.valueToTree(SmaatoUserExtData.of("keywords", "gender", 1)))
                                .build())
                        .build())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = smaatoBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getUser)
                .extracting(User::getExt)
                .containsOnly(ExtUser.builder().build());
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .extracting(BidRequest::getUser)
                .extracting(User::getGender)
                .containsOnly("gender");
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid", null);

        // when
        final Result<List<BidderBid>> result = smaatoBidder.makeBids(httpCall, null);

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
                mapper.writeValueAsString(null), null);

        // when
        final Result<List<BidderBid>> result = smaatoBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfNotSupportedMarkupType() throws JsonProcessingException {
        // given
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap().set("X-SMT-ADTYPE", "anyType");
        final HttpCall<BidRequest> httpCall = givenHttpCall(BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))), headers);

        // when
        final Result<List<BidderBid>> result = smaatoBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("Unknown markup type anyType"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorIfMarkupTypeIsBlank() throws JsonProcessingException {
        // given
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap().set("X-SMT-ADTYPE", "");
        final HttpCall<BidRequest> httpCall = givenHttpCall(BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123").adm("adm"))), headers);

        // when
        final Result<List<BidderBid>> result = smaatoBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("Invalid ad markup adm"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnCorrectBidIfAdMarkTypeIsReachmedia() throws JsonProcessingException {
        // given
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap().set("X-SMT-ADTYPE", "Richmedia");
        final HttpCall<BidRequest> httpCall = givenHttpCall(BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123").adm("{\"richmedia\":{\"mediadata\":"
                                + "{\"content\":\"<div>hello</div>\", \"w\":350,\"h\":50},\"impressiontrackers\":"
                                + "[\"//prebid-test.smaatolabs.net/track/imp/1\",\"//prebid-test.smaatolabs.net/track"
                                + "/imp/2\"],\"clicktrackers\":[\"//prebid-test.smaatolabs.net/track/click/1\","
                                + "\"//prebid-test.smaatolabs.net/track/click/2\"]}}"))), headers);

        // when
        final Result<List<BidderBid>> result = smaatoBidder.makeBids(httpCall, null);

        // then
        final Bid expectedBid = Bid.builder()
                .impid("123")
                .adm("<div onclick=\"fetch(decodeURIComponent('%2F%2Fprebid-test.smaatolabs.net%2Ftrack%2Fclick%2F1'),"
                        + " {cache: 'no-cache'});fetch(decodeURIComponent('%2F%2Fprebid-test.smaatolabs.net%2Ftrack%2"
                        + "Fclick%2F2'), {cache: 'no-cache'});\"><div>hello</div><img src=\"//prebid-test.smaatolabs."
                        + "net/track/imp/1\" alt=\"\" width=\"0\" height=\"0\"/><img src=\"//prebid-test.smaatolabs."
                        + "net/track/imp/2\" alt=\"\" width=\"0\" height=\"0\"/></div>")
                .build();
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(expectedBid, banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnCorrectBidIfAdMarkTypeIsImg() throws JsonProcessingException {
        // given
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap().set("X-SMT-ADTYPE", "Img");
        final HttpCall<BidRequest> httpCall = givenHttpCall(BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123").adm("{\"image\":{\"img\":{\"url\":\""
                                + "//prebid-test.smaatolabs.net/img/320x50.jpg\",\"w\":350,\"h\":50,\"ctaurl\":\""
                                + "//prebid-test.smaatolabs.net/track/ctaurl/1\"},\"impressiontrackers\":[\""
                                + "//prebid-test.smaatolabs.net/track/imp/1\",\"//prebid-test.smaatolabs.net/track/"
                                + "imp/2\"],\"clicktrackers\":[\"//prebid-test.smaatolabs.net/track/click/1\",\""
                                + "//prebid-test.smaatolabs.net/track/click/2\"]}}"))), headers);

        // when
        final Result<List<BidderBid>> result = smaatoBidder.makeBids(httpCall, null);

        // then
        final Bid expectedBid = Bid.builder()
                .impid("123")
                .adm("<div style=\"cursor:pointer\" onclick=\"fetch(decodeURIComponent('%2F%2Fprebid-test.smaatolabs."
                        + "net%2Ftrack%2Fclick%2F1'.replace(/\\+/g, ' ')), {cache: 'no-cache'});fetch"
                        + "(decodeURIComponent('%2F%2Fprebid-test.smaatolabs.net%2Ftrack%2Fclick%2F2'.replace(/\\+/g,"
                        + " ' ')), {cache: 'no-cache'});;window.open(decodeURIComponent('%2F%2Fprebid-test.smaatolabs."
                        + "net%2Ftrack%2Fctaurl%2F1'.replace(/\\+/g, ' ')));\"><img src=\"//prebid-test.smaatolabs.net"
                        + "/img/320x50.jpg\" width=\"350\" height=\"50\"/><img src=\"//prebid-test.smaatolabs.net/"
                        + "track/imp/1\" alt=\"\" width=\"0\" height=\"0\"/><img src=\"//prebid-test.smaatolabs.net/"
                        + "track/imp/2\" alt=\"\" width=\"0\" height=\"0\"/></div>")
                .build();
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(expectedBid, banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnCorrectBidIfAdMarkTypeIsVideo() throws JsonProcessingException {
        // given
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap().set("X-SMT-ADTYPE", "Video");
        final HttpCall<BidRequest> httpCall = givenHttpCall(BidRequest.builder()
                        .imp(singletonList(Imp.builder().id("123").build()))
                        .build(),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123").adm("<?xml version=\"1.0\" encoding="
                                + "\"UTF-8\" standalone=\"no\"?><VAST version=\"2.0\"></VAST>"))), headers);

        // when
        final Result<List<BidderBid>> result = smaatoBidder.makeBids(httpCall, null);

        // then
        final Bid expectedBid = Bid.builder()
                .impid("123")
                .adm("Video")
                .build();
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(expectedBid, video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(BidResponse.builder().build()), null);

        // when
        final Result<List<BidderBid>> result = smaatoBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyResultWhenResponseWithNoContent() {
        // given
        final HttpCall<BidRequest> httpCall = HttpCall
                .success(null, HttpResponse.of(204, null, null), null);

        // when
        final Result<List<BidderBid>> result = smaatoBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
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
                .banner(Banner.builder().id("banner_id").build()).ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpSmaato.of("publisherId", "adspaceId")))))
                .build();
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder().bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
    }

    private static HttpCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body, MultiMap headers) {
        return HttpCall.success(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, headers, body),
                null);
    }
}
