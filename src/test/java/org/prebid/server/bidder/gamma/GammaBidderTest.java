package org.prebid.server.bidder.gamma;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.BidRequest.BidRequestBuilder;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.gamma.model.GammaBid;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.gamma.ExtImpGamma;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.groups.Tuple.tuple;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;

public class GammaBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com/";

    private GammaBidder gammaBidder;

    @Before
    public void setUp() {
        gammaBidder = new GammaBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new GammaBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<Void>>> result = gammaBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("ext.bidder.publisher not provided"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenBannerAndVideoAreAbsent() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .banner(null)
                .id("iId")
                .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<Void>>> result = gammaBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(2).containsOnly(
                BidderError.badInput("Gamma only supports banner and video media types. Ignoring imp id= iId"),
                BidderError.badInput("No valid impressions"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenExtImpGammaIdIsBlank() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpGamma.of("", "zid", "wid")))));

        // when
        final Result<List<HttpRequest<Void>>> result = gammaBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).containsOnly(BidderError.badInput("PartnerID is empty"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenExtImpGammaWidIsBlank() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpGamma.of("id", "zid", "")))));

        // when
        final Result<List<HttpRequest<Void>>> result = gammaBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).containsOnly(BidderError.badInput("WebID is empty"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenExtImpGammaZidIsBlank() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpGamma.of("id", "", "wid")))));

        // when
        final Result<List<HttpRequest<Void>>> result = gammaBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).containsOnly(BidderError.badInput("ZoneID is empty"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldFillMethodAndUrlAndExpectedHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpGamma.of("id", "zid", "wid")))));

        // when
        final Result<List<HttpRequest<Void>>> result = gammaBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).doesNotContainNull()
                .hasSize(1).element(0)
                .returns(HttpMethod.GET, HttpRequest::getMethod)
                .returns("https://test.endpoint.com/?id=id&zid=zid&wid=wid&bidid=&hb=pbmobile",
                        HttpRequest::getUri);
        assertThat(result.getValue().get(0).getHeaders()).isNotNull()
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple("Accept", "*/*"),
                        tuple("Connection", "keep-alive"),
                        tuple("Cache-Control", "no-cache"),
                        tuple("Accept-Encoding", "gzip, deflate"),
                        tuple("x-openrtb-version", "2.5"));
    }

    @Test
    public void makeHttpRequestsShouldFillMethodAndUrlAndExpectedHeadersWhenDeviceAndAppProvided() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder
                        .device(Device.builder()
                                .ip("123.123.123.12")
                                .model("Model")
                                .os("OS")
                                .ua("userAgent")
                                .language("fr")
                                .ifa("ifa")
                                .dnt(8)
                                .build())
                        .app(App.builder()
                                .id("appId")
                                .bundle("bundle")
                                .name("appName")
                                .build()),
                impBuilder -> impBuilder

                        .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpGamma.of("id", "zid", "wid")))));

        // when
        final Result<List<HttpRequest<Void>>> result = gammaBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).doesNotContainNull()
                .hasSize(1).element(0)
                .returns(HttpMethod.GET, HttpRequest::getMethod)
                .returns(
                        "https://test.endpoint.com/?id=id&zid=zid&wid=wid&bidid=&hb=pbmobile"
                                + "&device_ip=123.123.123.12&device_model=Model&device_os=OS&device_ua=userAgent"
                                + "&device_ifa=ifa&app_id=appId&app_bundle=bundle&app_name=appName",
                        HttpRequest::getUri);
        assertThat(result.getValue().get(0).getHeaders()).isNotNull()
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple("Accept", "*/*"),
                        tuple("Connection", "keep-alive"),
                        tuple("Cache-Control", "no-cache"),
                        tuple("Accept-Encoding", "gzip, deflate"),
                        tuple("User-Agent", "userAgent"),
                        tuple("x-openrtb-version", "2.5"),
                        tuple("X-Forwarded-For", "123.123.123.12"),
                        tuple("Accept-Language", "fr"),
                        tuple("DNT", "8"));
    }

    @Test
    public void makeHttpRequestsShouldNotSendBody() {
        // given
        final BidRequest bidRequest = givenBidRequest(bidRequestBuilder -> bidRequestBuilder
                .ext(mapper.valueToTree(ExtPrebid.of(null, ExtImpGamma.of("id", "zid", "wid")))));

        // when
        final Result<List<HttpRequest<Void>>> result = gammaBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1).element(0).returns(null, HttpRequest::getBody);
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<Void> httpCall = givenHttpCall(null);

        // when
        final Result<List<BidderBid>> result = gammaBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("bad server response: body is empty");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldSetAdmFromVastXmlIsPresentAndVideoType() throws JsonProcessingException {
        // given
        final Imp imp = Imp.builder().id("impId").video(Video.builder().build()).build();
        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(imp)).build();

        final String adm = "ADM";
        final GammaBid bid = GammaBid.builder().id("impId").vastXml(adm).build();
        final HttpCall<Void> httpCall = givenHttpCall(mapper.writeValueAsString(
                BidResponse.builder()
                        .id("impId")
                        .cur("USD")
                        .seatbid(singletonList(SeatBid.builder()
                                .bid(singletonList(bid))
                                .build()))
                        .build()));

        // when
        final Result<List<BidderBid>> result = gammaBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();

        final Bid expectedBid = Bid.builder().id("impId").adm(adm).build();
        assertThat(result.getValue()).containsOnly(BidderBid.of(expectedBid, video, "USD"));
    }

    @Test
    public void makeBidsShouldSetAdmFromVastXmlAndNurlFromVastUrlAndVideoType() throws JsonProcessingException {
        // given
        final Imp imp = Imp.builder().id("impId").video(Video.builder().build()).build();
        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(imp)).build();

        final String adm = "ADM";
        final String nurl = "NURL";
        final GammaBid bid = GammaBid.builder().id("impId").vastXml(adm).vastUrl(nurl).build();
        final HttpCall<Void> httpCall = givenHttpCall(mapper.writeValueAsString(
                BidResponse.builder()
                        .id("impId")
                        .cur("USD")
                        .seatbid(singletonList(SeatBid.builder()
                                .bid(singletonList(bid))
                                .build()))
                        .build()));

        // when
        final Result<List<BidderBid>> result = gammaBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();

        final Bid expectedBid = Bid.builder().id("impId").adm(adm).nurl(nurl).build();
        assertThat(result.getValue()).containsOnly(BidderBid.of(expectedBid, video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenNoVastXmlAndVideoType() throws JsonProcessingException {
        // given
        final Imp imp = Imp.builder().id("impId").video(Video.builder().build()).build();
        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(imp)).build();

        final HttpCall<Void> httpCall = givenHttpCall(mapper.writeValueAsString(
                BidResponse.builder()
                        .id("impId")
                        .seatbid(singletonList(SeatBid.builder()
                                .bid(singletonList(Bid.builder().build()))
                                .build()))
                        .build()));

        // when
        final Result<List<BidderBid>> result = gammaBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).containsOnly(BidderError.badServerResponse(
                "Missing Ad Markup. Run with request.debug = 1 for more info"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenNoAdmAndNotVideoType() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder().imp(emptyList()).build();
        final HttpCall<Void> httpCall = givenHttpCall(mapper.writeValueAsString(
                BidResponse.builder()
                        .id("id")
                        .cur("USD")
                        .seatbid(singletonList(SeatBid.builder()
                                .bid(singletonList(Bid.builder().build()))
                                .build()))
                        .build()));

        // when
        final Result<List<BidderBid>> result = gammaBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).containsOnly(BidderError.badServerResponse(
                "Missing Ad Markup. Run with request.debug = 1 for more info"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerWhenNoProvided() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder().imp(emptyList()).build();
        final Bid bid = Bid.builder().adm("ADM").build();
        final HttpCall<Void> httpCall = givenHttpCall(mapper.writeValueAsString(
                BidResponse.builder()
                        .id("id")
                        .cur("USD")
                        .seatbid(singletonList(SeatBid.builder()
                                .bid(singletonList(bid))
                                .build()))
                        .build()));

        // when
        final Result<List<BidderBid>> result = gammaBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();

        final GammaBid expectedBid = GammaBid.builder().adm("ADM").build();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(expectedBid, banner, "USD"));
    }

    private static BidRequest givenBidRequest(
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer);
    }

    private static BidRequest givenBidRequest(
            Function<BidRequestBuilder, BidRequestBuilder> bidRequestCustomizer,
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                .imp(singletonList(givenImp(impCustomizer))))
                .build();
    }

    private static Imp givenImp(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder().banner(Banner.builder().build())).build();
    }

    private static HttpCall<Void> givenHttpCall(String body) {
        return HttpCall.success(
                HttpRequest.<Void>builder().build(),
                HttpResponse.of(200, null, body),
                null);
    }
}

