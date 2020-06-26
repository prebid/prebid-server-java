package org.prebid.server.bidder.adocean;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import io.netty.handler.codec.http.HttpHeaderValues;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.adocean.model.AdoceanResponseAdUnit;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.smartrtb.SmartrtbBidder;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.adocean.ExtImpAdocean;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static java.util.Arrays.asList;

public class AdoceanBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://{{Host}}";

    private AdoceanBidder adoceanBidder;

    @Before
    public void setUp() {
        adoceanBidder = new AdoceanBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SmartrtbBidder("invalid_url", jacksonMapper))
                .withMessage("URL supplied is not valid: invalid_url");
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpressionListSizeIsZero() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(emptyList())
                .build();

        // when
        final Result<List<HttpRequest<Void>>> result = adoceanBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("No impression in the bid request"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .id("123")
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));
        // when
        final Result<List<HttpRequest<Void>>> result = adoceanBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Cannot deserialize instance");
    }

    @Test
    public void makeHttpRequestsShouldSetReturnErrorIfRequestExists() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .user(User.builder()
                        .ext(mapper.valueToTree(ExtUser.builder()
                                .consent("COwK6gaOwK6gaFmAAAENAPCAAAAAAAAAAAAAAAAAAAAA."
                                        + "IFoEUQQgAIQwgIwQABAEAAAAOIAACAIAAAAQAIAgEAACEAAAAAgAQBAAAAAAAGBAAgAAAAAA"
                                        + "AFAAECAAAgAAQARAEQAAAAAJAAIAAgAAAYQEAAAQmAgBC3ZAYzUw").build()))
                        .build())
                .imp(asList(Imp.builder()
                                .id("ao-test")
                                .banner(Banner.builder().format(singletonList(Format.builder().w(300).h(250).build()))
                                        .id("banner_id").build()).ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpAdocean.of("myao.adocean.pl", "tmYF.DMl7ZBq.Nqt2Bq4FutQTJfTpxCOmtNPZoQUDcL.G7",
                                        "adoceanmyaozpniqismex")))).build(),
                        Imp.builder()
                                .id("ao-test")
                                .banner(Banner.builder().format(singletonList(Format.builder().w(300).h(250).build()))
                                        .id("banner_id").build()).ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpAdocean.of("myao.adocean.pl", "tmYF.DMl7ZBq.Nqt2Bq4FutQTJfTpxCOmtNPZoQUDcL.G7",
                                        "adoceanmyaozpniqis")))).build()))
                .test(1)

                .build();

        // when
        final Result<List<HttpRequest<Void>>> result = adoceanBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Request already exists");
    }

    @Test
    public void makeHttpRequestsShouldSetExpectedRequestUrl() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .user(User.builder()
                        .ext(mapper.valueToTree(ExtUser.builder()
                                .consent("COwK6gaOwK6gaFmAAAENAPCAAAAAAAAAAAAAAAAAAAAA.IFoEUQQgAIQwgIwQABAEAAAAOIAAC"
                                        + "AIAAAAQAIAgEAACEAAAAAgAQBAAAAAAAGBAAgAAAAAAAFAAECAAAgAAQARAEQAAAAAJAAIAAgA"
                                        + "AAYQEAAAQmAgBC3ZAYzUw").build()))
                        .build())
                .imp(singletonList(Imp.builder()
                        .id("ao-test")
                        .banner(Banner.builder().format(singletonList(Format.builder().w(300).h(250).build()))
                                .id("banner_id").build()).ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpAdocean.of("myao.adocean.pl", "tmYF.DMl7ZBq.Nqt2Bq4FutQTJfTpxCOmtNPZoQUDcL.G7",
                                        "adoceanmyaozpniqismex")))).build()))
                .test(1)
                .build();

        // when
        final Result<List<HttpRequest<Void>>> result = adoceanBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsOnly("https://myao.adocean.pl/_10000000/ad.json?aid=adoceanmyaozpniqismex:ao-test&gdpr=1&gdpr_consent=COwK6gaOwK6gaFmAAAENAPCAAAAAAAAAAAAAAAAAAAAA.IFoEUQQgAIQwgIwQABAEAAAAOIAACAIAAAAQAIAgEAACEAAAAAgAQBAAAAAAAGBAAgAAAAAAAFAAECAAAgAAQARAEQAAAAAJAAIAAgAAAYQEAAAQmAgBC3ZAYzUw&id=tmYF.DMl7ZBq.Nqt2Bq4FutQTJfTpxCOmtNPZoQUDcL.G7&nc=1&nosecure=1&pbsrv_v=1.0.0");
    }

    @Test
    public void makeHttpRequestsShouldSetExpectedHeadersIfDeviceIpIsPresent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .user(User.builder()
                        .ext(mapper.valueToTree(ExtUser.builder()
                                .consent("COwK6gaOwK6gaFmAAAENAPCAAAAAAAAAAAAAAAAAAAAA.IFoEUQQgAIQwgIwQABAEAAAAOIAAC"
                                        + "AIAAAAQAIAgEAACEAAAAAgAQBAAAAAAAGBAAgAAAAAAAFAAECAAAgAAQARAEQAAAAAJAAIAAg"
                                        + "AAAYQEAAAQmAgBC3ZAYzUw").build()))
                        .build())
                .imp(singletonList(Imp.builder()
                        .id("ao-test")
                        .banner(Banner.builder().format(singletonList(Format.builder().w(300).h(250).build()))
                                .id("banner_id").build())
                                .ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpAdocean.of("myao.adocean.pl", "tmYF.DMl7ZBq.Nqt2Bq4FutQTJfTpxCOmtNPZoQUDcL.G7",
                                        "adoceanmyaozpniqismex")))).build()))
                .test(1)
                .device(Device.builder().ip("192.168.1.1").build())
                .site(Site.builder().page("http://www.example.com").build())
                .build();

        // when
        final Result<List<HttpRequest<Void>>> result = adoceanBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue().get(0).getHeaders()).isNotNull()
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()),
                        tuple(HttpUtil.X_FORWARDED_FOR_HEADER.toString(), "192.168.1.1"),
                        tuple(HttpUtil.REFERER_HEADER.toString(), "http://www.example.com"));
    }

    @Test
    public void makeHttpRequestsShouldSetExpectedHeadersIfDeviceIpv6IsPresent() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .user(User.builder()
                        .ext(mapper.valueToTree(ExtUser.builder()
                                .consent("COwK6gaOwK6gaFmAAAENAPCAAAAAAAAAAAAAAAAAAAAA.IFoEUQQgAIQwgIwQABAEAAAAOIAACA"
                                        + "IAAAAQAIAgEAACEAAAAAgAQBAAAAAAAGBAAgAAAAAAAFAAECAAAgAAQARAEQAAAAAJAAIAAgAA"
                                        + "AYQEAAAQmAgBC3ZAYzUw").build()))
                        .build())
                .imp(singletonList(Imp.builder()
                        .id("ao-test")
                        .banner(Banner.builder().format(singletonList(Format.builder().w(300).h(250).build()))
                                .id("banner_id").build()).ext(mapper.valueToTree(ExtPrebid.of(null,
                                ExtImpAdocean.of("myao.adocean.pl", "tmYF.DMl7ZBq.Nqt2Bq4FutQTJfTpxCOmtNPZoQUDcL.G7",
                                        "adoceanmyaozpniqismex")))).build()))
                .test(1)
                .device(Device.builder().ipv6("2001:0db8:85a3:0000:0000:8a2e:0370:7334").build())
                .site(Site.builder().page("http://www.example.com").build())
                .build();

        // when
        final Result<List<HttpRequest<Void>>> result = adoceanBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue().get(0).getHeaders()).isNotNull()
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()),
                        tuple(HttpUtil.X_FORWARDED_FOR_HEADER.toString(), "2001:0db8:85a3:0000:0000:8a2e:0370:7334"),
                        tuple(HttpUtil.REFERER_HEADER.toString(), "http://www.example.com"));
    }

    @Test
    public void makeBidsShouldReturnEmptyResultWhenResponseWithNoContent() {
        // given
        final HttpCall<Void> httpCall = HttpCall
                .success(null, HttpResponse.of(204, null, null), null);

        // when
        final Result<List<BidderBid>> result = adoceanBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyResultWhenResponseStatusIsNotOk() {
        // given
        final HttpCall<Void> httpCall = HttpCall
                .success(null, HttpResponse.of(404, null, null), null);

        // when
        final Result<List<BidderBid>> result = adoceanBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Unexpected HTTP status 404.");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
    }

    @Test
    public void makeBidsShouldReturnEmptyResultWhenResponseStatusIsBadRequest() {
        // given
        final HttpCall<Void> httpCall = HttpCall
                .success(null, HttpResponse.of(400, null, null), null);

        // when
        final Result<List<BidderBid>> result = adoceanBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Invalid request.");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_input);
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<Void> httpCall = givenHttpCall(null, "");

        // when
        final Result<List<BidderBid>> result = adoceanBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getErrors().get(0).getMessage())
                .startsWith("Failed to decode: No content to map due to end-of-input");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnCorrectBidderBid() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("impId")
                        .build()))
                .build();

        final List<AdoceanResponseAdUnit> adoceanResponseAdUnit = asList(AdoceanResponseAdUnit.builder()
                .id("adoceanmyaozpniqismex")
                .price("1")
                .winUrl("https://win-url.com")
                .statsUrl("https://stats-url.com")
                .code(" <!-- code 1 --> ")
                .currency("EUR")
                .width("300")
                .height("250")
                .crid("0af345b42983cc4bc0")
                .error("false")
                .build(),
                AdoceanResponseAdUnit.builder()
                        .id("adoceanmyaozpniqis")
                        .price("1")
                        .winUrl("https://win-url.com")
                        .statsUrl("https://stats-url.com")
                        .code(" <!-- code 1 --> ")
                        .currency("EUR")
                        .width("300")
                        .height("250")
                        .crid("0af345b42983cc4bc0")
                        .error("false")
                        .build());

        final HttpCall<Void> httpCall = givenHttpCall(null, mapper.writeValueAsString(adoceanResponseAdUnit));

        // when
        final Result<List<BidderBid>> result = adoceanBidder.makeBids(httpCall, bidRequest);

        // then
        final StringBuilder admBuilder = new StringBuilder();
        admBuilder.append("<script>\n")
                .append("\t+function() {\n")
                .append("\t\tvar wu = \"https://win-url.com\";\n")
                .append("\t\tvar su = \"https://stats-url.com\".replace(/\\[TIMESTAMP\\]/, Date.now());\n")
                .append("\t\tif (wu && !(navigator.sendBeacon && navigator.sendBeacon(wu))) {\n\n")
                .append("\t\t\t(new Image(1,1)).src = wu\n")
                .append("\t\t}\n")
                .append("\n")
                .append("\t\tif (su && !(navigator.sendBeacon && navigator.sendBeacon(su))) {\n")
                .append("\t\t\t(new Image(1,1)).src = su\n")
                .append("\t\t}\n")
                .append("\t}();\n")
                .append("</script>")
                .append(" <!-- code 1 --> ");
        final String adm = admBuilder.toString();

        final BidderBid expected = BidderBid.of(
                Bid.builder()
                        .id("adoceanmyaozpniqismex")
                        .impid("ao-test")
                        .adm(adm)
                        .price(BigDecimal.valueOf(1))
                        .crid("0af345b42983cc4bc0")
                        .w(300)
                        .h(250)
                        .build(),
                BidType.banner, "EUR");
        assertThat(result.getValue().get(0).getBid().getAdm()).isEqualTo(adm);
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).doesNotContainNull().hasSize(1).element(0).isEqualTo(expected);
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
                        ExtImpAdocean.of("myao.adocean.pl", "tmYF.DMl7ZBq.Nqt2Bq4FutQTJfTpxCOmtNPZoQUDcL.G7",
                                "adoceanmyaozpniqismex")))))
                .build();
    }

    private static HttpCall<Void> givenHttpCall(String requestBody, String responseBody) {
        return HttpCall.success(
                HttpRequest.<Void>builder()
                        .body(requestBody)
                        .uri("https://myao.adocean.pl/_10000000/ad.json?aid=adoceanmyaozpniqismex%3Aao-test&gdpr=1&gdpr_consent=COwK6gaOwK6gaFmAAAENAPCAAAAAAAAAAAAAAAAAAAAA.IFoEUQQgAIQwgIwQABAEAAAAOIAACAIAAAAQAIAgEAACEAAAAAgAQBAAAAAAAGBAAgAAAAAAAFAAECAAAgAAQARAEQAAAAAJAAIAAgAAAYQEAAAQmAgBC3ZAYzUw&id=tmYF.DMl7ZBq.Nqt2Bq4FutQTJfTpxCOmtNPZoQUDcL.G7&nc=1&nosecure=1&pbsrv_v=1.0.0")
                        .build(),
                HttpResponse.of(200, null, responseBody), null);
    }
}
