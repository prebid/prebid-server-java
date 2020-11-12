package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToIgnoreCase;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
public class AdformTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromAdform() throws IOException, JSONException {
        // given
        // adform bid response for imp 12
        WIRE_MOCK_RULE.stubFor(get(urlPathEqualTo("/adform-exchange"))
                .withQueryParam("CC", equalTo("1"))
                .withQueryParam("rp", equalTo("4"))
                .withQueryParam("fd", equalTo("1"))
                .withQueryParam("stid", equalTo("tid"))
                .withQueryParam("pt", equalTo("gross"))
                .withQueryParam("ip", equalTo("193.168.244.1"))
                .withQueryParam("adid", equalTo("ifaId"))
                .withQueryParam("gdpr", equalTo("0"))
                .withQueryParam("gdpr_consent", equalTo("consentValue"))
                .withQueryParam("url", equalTo("https://adform.com?a=b"))
                // bWlkPTE1JnJjdXI9VVNEJm1rdj1jb2xvcjpyZWQmbWt3PXJlZCZjZGltcz0zMDB4NjAwJm1pbnA9Mi41MA is Base64 encoded
                // "mid=15&rcur=USD&mkv=color:red&mkw=red&cdims=300X600&minp=2.50"
                .withQueryParam("bWlkPTE1JnJjdXI9VVNEJm1rdj1jb2xvcjpyZWQmbWt3PXJlZCZjZGltcz0zMDB4NjAwJm1pbnA9Mi41MA",
                        equalTo(""))
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=utf-8"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("User-Agent", equalTo("userAgent"))
                .withHeader("X-Request-Agent", equalTo("PrebidAdapter 0.1.3"))
                .withHeader("X-Forwarded-For", equalTo("193.168.244.1"))
                .withHeader("Cookie", equalTo("uid=AF-UID;DigiTrust.v1.identity=eyJpZCI6ImlkIiwidmVyc2l"
                        + "vbiI6MSwia2V5diI6MTIzLCJwcml2YWN5Ijp7Im9wdG91dCI6ZmFsc2V9fQ"))
                .withRequestBody(absent())
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/adform/test-adform-bid-response-1.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/adform/test-cache-adform-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/adform/test-cache-adform-response.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"adform":"AF-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7ImFkZm9ybSI6IkFGLVVJRCJ9fQ==")
                .body(jsonFrom("openrtb2/adform/test-auction-adform-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/adform/test-auction-adform-response.json",
                response, singletonList("adform"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    public void auctionShouldRespondWithBidsFromAdform() throws IOException {
        // given
        // adform bid response for ad unit 12
        WIRE_MOCK_RULE.stubFor(get(urlPathEqualTo("/adform-exchange"))
                .withQueryParam("CC", equalTo("1"))
                .withQueryParam("rp", equalTo("4"))
                .withQueryParam("fd", equalTo("1"))
                .withQueryParam("stid", equalTo("tid"))
                .withQueryParam("ip", equalTo("193.168.244.1"))
                .withQueryParam("adid", equalTo("ifaId"))
                .withQueryParam("gdpr", equalTo("1"))
                .withQueryParam("gdpr_consent", equalTo("consent1"))
                .withQueryParam("pt", equalTo("gross"))
                // bWlkPTE1JnJjdXI9VVNE is Base64 encoded "mid=15&rcur=USD"
                .withQueryParam("bWlkPTE1JnJjdXI9VVNE", equalTo(""))
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=utf-8"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("User-Agent", equalTo("userAgent"))
                .withHeader("X-Request-Agent", equalTo("PrebidAdapter 0.1.3"))
                .withHeader("X-Forwarded-For", equalTo("193.168.244.1"))
                .withHeader("Cookie", equalTo("uid=AF-UID;DigiTrust.v1.identity"
                        //{"id":"id","version":1,"keyv":123,"privacy":{"optout":true}}
                        + "=eyJpZCI6ImlkIiwidmVyc2lvbiI6MSwia2V5diI6MTIzLCJwcml2YWN5Ijp7Im9wdG91dCI6dHJ1ZX19"))
                .withRequestBody(absent())
                .willReturn(aResponse().withBody(jsonFrom("auction/adform/test-adform-bid-response-1.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("auction/adform/test-cache-adform-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("auction/adform/test-cache-adform-response.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"adform":"AF-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7ImFkZm9ybSI6IkFGLVVJRCJ9fQ==")
                .queryParam("debug", "1")
                .body(jsonFrom("auction/adform/test-auction-adform-request.json"))
                .post("/auction");

        // then
        assertThat(response.header("Cache-Control")).isEqualTo("no-cache, no-store, must-revalidate");
        assertThat(response.header("Pragma")).isEqualTo("no-cache");
        assertThat(response.header("Expires")).isEqualTo("0");
        assertThat(response.header("Access-Control-Allow-Credentials")).isEqualTo("true");
        assertThat(response.header("Access-Control-Allow-Origin")).isEqualTo("http://www.example.com");

        final String expectedAuctionResponse = legacyAuctionResponseFrom(
                "auction/adform/test-auction-adform-response.json",
                response, singletonList("adform"));
        assertThat(response.asString()).isEqualTo(expectedAuctionResponse);
    }
}
