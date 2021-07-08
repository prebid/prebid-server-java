package org.prebid.server.bidder.eplanning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.eplanning.model.HbResponse;
import org.prebid.server.bidder.eplanning.model.HbResponseAd;
import org.prebid.server.bidder.eplanning.model.HbResponseSpace;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.eplanning.ExtImpEplanning;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

public class EplanningBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://eplanning.com";

    private EplanningBidder eplanningBidder;

    @Before
    public void setUp() {
        eplanningBidder = new EplanningBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new EplanningBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpBannerIsNull() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .banner(null));

        // when
        final Result<List<HttpRequest<Void>>> result = eplanningBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("EPlanning only supports banner Imps. Ignoring Imp ID=123"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<Void>>> result = eplanningBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage())
                .startsWith("Ignoring imp id=123, error while decoding extImpBidder, err: Cannot deserialize instance");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtClientIdIsBlank() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                mapper.createObjectNode().put("ci", "")))));

        // when
        final Result<List<HttpRequest<Void>>> result = eplanningBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("Ignoring imp id=123, no ClientID present"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfEndpointUrlComposingFails() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                requestBuilder -> requestBuilder
                        .site(Site.builder().domain("invalid domain").build()),
                identity());

        // when
        final Result<List<HttpRequest<Void>>> result = eplanningBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getMessage())
                            .startsWith("Invalid url: https://eplanning.com/clientId/1/invalid domain/ROS");
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                });
    }

    @Test
    public void makeHttpRequestsShouldSendSingleGetRequestWithNullBody() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<Void>>> result = eplanningBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .hasSize(1)
                .extracting(HttpRequest::getBody)
                .containsNull();
        assertThat(result.getValue())
                .extracting(HttpRequest::getMethod)
                .containsExactly(HttpMethod.GET);
    }

    @Test
    public void makeHttpRequestsShouldSetCorrectHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<Void>>> result = eplanningBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getHeaders)
                .flatExtracting(MultiMap::entries)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactly(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()));
    }

    @Test
    public void makeHttpRequestsShouldSetAdditionalHeadersIfDeviceFieldsAreNotEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                requestBuilder -> requestBuilder
                        .device(Device.builder()
                                .ua("user_agent")
                                .language("language")
                                .ip("test_ip")
                                .dnt(1)
                                .build()),
                identity());

        // when
        final Result<List<HttpRequest<Void>>> result = eplanningBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getHeaders)
                .flatExtracting(MultiMap::entries)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .contains(
                        tuple(HttpUtil.USER_AGENT_HEADER.toString(), "user_agent"),
                        tuple(HttpUtil.ACCEPT_LANGUAGE_HEADER.toString(), "language"),
                        tuple(HttpUtil.X_FORWARDED_FOR_HEADER.toString(), "test_ip"),
                        tuple(HttpUtil.DNT_HEADER.toString(), "1"));
    }

    @Test
    public void makeHttpRequestsShouldSetCorrectUriWithDefaults() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<Void>>> result = eplanningBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly(
                        "https://eplanning.com/clientId/1/FILE/ROS?r=pbs&ncb=1&ur=FILE&e=testadun_itco_de%3A1x1");
    }

    @Test
    public void makeHttpRequestsShouldSetCorrectUriWithSitePageAndDomain() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                requestBuilder -> requestBuilder
                        .site(Site.builder().page("https://www.example.com").domain("DOMAIN").build()),
                identity());

        // when
        final Result<List<HttpRequest<Void>>> result = eplanningBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly(
                        "https://eplanning.com/clientId/1/DOMAIN/ROS?r=pbs&ncb=1&ur=https%3A%2F%2Fwww.example.com&e="
                                + "testadun_itco_de%3A1x1");
    }

    @Test
    public void makeHttpRequestsShouldSetCorrectUriIfSiteDomainIsBlank() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                requestBuilder -> requestBuilder
                        .site(Site.builder().page("https://www.example.com").domain("").build()),
                identity());

        // when
        final Result<List<HttpRequest<Void>>> result = eplanningBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://eplanning.com/clientId/1/www.example.com/ROS?r=pbs&ncb=1"
                        + "&ur=https%3A%2F%2Fwww.example.com&e=testadun_itco_de%3A1x1");
    }

    @Test
    public void makeHttpRequestsShouldSetCorrectUriWithSizeStringFromBannerWAndH() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .banner(Banner.builder()
                                .w(300)
                                .h(200)
                                .build()));

        // when
        final Result<List<HttpRequest<Void>>> result = eplanningBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://eplanning.com/clientId/1/FILE/ROS?r=pbs&ncb=1&ur=FILE&e=testadun_itco_de%3A"
                        + "300x200");
    }

    @Test
    public void makeHttpRequestsShouldSetCorrectUriWithSizeStringFromFormatForMobile() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.device(Device.builder().devicetype(1).build()),
                impBuilder -> impBuilder
                        .banner(Banner.builder()
                                .format(asList(Format.builder().w(300).h(50).build(),
                                        Format.builder().w(320).h(50).build()))
                                .build()));

        // when
        final Result<List<HttpRequest<Void>>> result = eplanningBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://eplanning.com/clientId/1/FILE/ROS?r=pbs&ncb=1&ur=FILE&e=testadun_itco_de%3A"
                        + "320x50");
    }

    @Test
    public void makeHttpRequestsShouldSetCorrectUriWithSizeStringFromFormatForDesktop() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.device(Device.builder().devicetype(2).build()),
                impBuilder -> impBuilder
                        .banner(Banner.builder()
                                .format(asList(Format.builder().w(300).h(600).build(),
                                        Format.builder().w(728).h(90).build()))
                                .build()));

        // when
        final Result<List<HttpRequest<Void>>> result = eplanningBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://eplanning.com/clientId/1/FILE/ROS?r=pbs&ncb=1&ur=FILE&e=testadun_itco_de%3A"
                        + "728x90");
    }

    @Test
    public void makeHttpRequestsShouldTolerateAndDropInvalidFormats() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.device(Device.builder().devicetype(2).build()),
                impBuilder -> impBuilder
                        .banner(Banner.builder()
                                .format(asList(Format.builder().w(null).h(600).build(),
                                        Format.builder().w(728).h(90).build()))
                                .build()));

        // when
        final Result<List<HttpRequest<Void>>> result = eplanningBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://eplanning.com/clientId/1/FILE/ROS?r=pbs&ncb=1&ur=FILE&e=testadun_itco_de%3A"
                        + "728x90");
    }

    @Test
    public void makeHttpRequestsShouldSetUriWithSize1x1WhenSizeWasNotFoundInPriority() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                bidRequestBuilder -> bidRequestBuilder.device(Device.builder().devicetype(2).build()),
                impBuilder -> impBuilder
                        .banner(Banner.builder()
                                .format(asList(Format.builder().w(301).h(600).build(),
                                        Format.builder().w(729).h(90).build()))
                                .build()));

        // when
        final Result<List<HttpRequest<Void>>> result = eplanningBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://eplanning.com/clientId/1/FILE/ROS?r=pbs&ncb=1&ur=FILE&e=testadun_itco_de%3A"
                        + "1x1");
    }

    @Test
    public void makeHttpRequestsShouldSetCorrectUriWithUserId() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                requestBuilder -> requestBuilder
                        .user(User.builder().buyeruid("Buyer-ID").build()),
                identity());

        // when
        final Result<List<HttpRequest<Void>>> result = eplanningBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://eplanning.com/clientId/1/FILE/ROS?r=pbs&ncb=1&ur=FILE&e=testadun_itco_de%3A"
                        + "1x1&uid=Buyer-ID");
    }

    @Test
    public void makeHttpRequestsShouldSetCorrectUriWithDeviceIp() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                requestBuilder -> requestBuilder
                        .device(Device.builder().ip("123.321.321.123").build()),
                identity());

        // when
        final Result<List<HttpRequest<Void>>> result = eplanningBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly(
                        "https://eplanning.com/clientId/1/FILE/ROS?r=pbs&ncb=1&ur=FILE&e=testadun_itco_de%3A1x1"
                                + "&ip=123.321.321.123");
    }

    @Test
    public void makeHttpRequestsShouldSetCorrectUriWithApp() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                requestBuilder -> requestBuilder
                        .app(App.builder().id("id").name("appName").build())
                        .device(Device.builder().ifa("ifa").build()),
                identity());

        // when
        final Result<List<HttpRequest<Void>>> result = eplanningBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://eplanning.com/clientId/1/FILE/ROS?r=pbs&ncb=1&e=testadun_itco_de%3A1x1&"
                        + "appn=appName&appid=id&ifa=ifa&app=1");
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<Void> httpCall = givenHttpCall("invalid");

        // when
        final Result<List<BidderBid>> result = eplanningBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Failed to decode: Unrecognized token");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidWithExpectedFields() throws JsonProcessingException {
        // given
        final HttpCall<Void> httpCall = givenHttpCall(
                mapper.writeValueAsString(HbResponse.of(
                        singletonList(HbResponseSpace.of("testadun_itco_de",
                                singletonList(HbResponseAd.builder()
                                        .adId("ad-id")
                                        .impressionId("imp-id")
                                        .price("3.3")
                                        .adM("some-adm")
                                        .crId("CR-ID")
                                        .width(500)
                                        .height(300)
                                        .build()))))));

        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<BidderBid>> result = eplanningBidder.makeBids(httpCall, bidRequest);

        // then
        final Bid expectedBid = Bid.builder()
                .id("imp-id")
                .adid("ad-id")
                .impid("123")
                .price(BigDecimal.valueOf(3.3))
                .adm("some-adm")
                .crid("CR-ID")
                .w(500)
                .h(300)
                .build();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .containsOnly(BidderBid.of(expectedBid, BidType.banner, null));
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfMissingAdunitCode() throws JsonProcessingException {
        // given
        final HttpCall<Void> httpCall = givenHttpCall(
                mapper.writeValueAsString(HbResponse.of(
                        singletonList(HbResponseSpace.of("1x1",
                                singletonList(HbResponseAd.builder()
                                        .adId("ad-id")
                                        .impressionId("imp-id")
                                        .price("3.3")
                                        .adM("some-adm")
                                        .crId("CR-ID")
                                        .width(1)
                                        .height(1)
                                        .build()))))));

        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(mapper.valueToTree(ExtPrebid.of(null,
                ExtImpEplanning.of("clientId", null)))));

        // when
        final Result<List<BidderBid>> result = eplanningBidder.makeBids(httpCall, bidRequest);

        // then
        final Bid expectedBid = Bid.builder()
                .id("imp-id")
                .adid("ad-id")
                .impid("123")
                .price(BigDecimal.valueOf(3.3))
                .adm("some-adm")
                .crid("CR-ID")
                .w(1)
                .h(1)
                .build();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .containsOnly(BidderBid.of(expectedBid, BidType.banner, null));
    }

    @Test
    public void makeBidsShouldNotCrashIfThereAreNoSpaces() throws JsonProcessingException {
        // given
        final HttpCall<Void> httpCall = givenHttpCall(
                mapper.writeValueAsString(HbResponse.of(null)));

        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<BidderBid>> result = eplanningBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldNotCrashIfThereAreNoAds() throws JsonProcessingException {
        // given
        final HttpCall<Void> httpCall = givenHttpCall(
                mapper.writeValueAsString(HbResponse.of(
                        singletonList(HbResponseSpace.of("adless", null)))));

        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<BidderBid>> result = eplanningBidder.makeBids(httpCall, bidRequest);

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
                .banner(Banner.builder().build())
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        ExtImpEplanning.of("clientId", "test_ad.-un(itco:de:")))))
                .build();
    }

    private static HttpCall<Void> givenHttpCall(String body) {
        return HttpCall.success(null, HttpResponse.of(200, null, body), null);
    }
}
