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
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
public class LifestreetTest extends IntegrationTest {

    private static final String LIFESTREET = "lifestreet";

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromLifestreet() throws IOException, JSONException {
        // given
        // lifestreet bid response for imp 7
        wireMockRule.stubFor(post(urlPathEqualTo("/lifestreet-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/lifestreet/test-lifestreet-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/lifestreet/test-lifestreet-bid-response-1.json"))));

        // lifestreet bid response for imp 71
        wireMockRule.stubFor(post(urlPathEqualTo("/lifestreet-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/lifestreet/test-lifestreet-bid-request-2.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/lifestreet/test-lifestreet-bid-response-2.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/lifestreet/test-cache-lifestreet-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/lifestreet/test-cache-lifestreet-response.json"))));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "192.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"lifestreet":"LS-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7ImxpZmVzdHJlZXQiOiJMUy1VSUQifX0=")
                .body(jsonFrom("openrtb2/lifestreet/test-auction-lifestreet-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/lifestreet/test-auction-lifestreet-response.json",
                response, singletonList(LIFESTREET));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    public void auctionShouldRespondWithBidsFromLifestreet() throws IOException {
        // given
        // lifestreet bid response for ad unit 8
        wireMockRule.stubFor(post(urlPathEqualTo("/lifestreet-exchange"))
                .withRequestBody(equalToJson(jsonFrom("auction/lifestreet/test-lifestreet-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("auction/lifestreet/test-lifestreet-bid-response-1.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("auction/lifestreet/test-cache-lifestreet-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("auction/lifestreet/test-cache-lifestreet-response.json"))));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "192.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                //this uids cookie value stands for {"uids":{"lifestreet":"LS-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7ImxpZmVzdHJlZXQiOiJMUy1VSUQifX0=")
                .queryParam("debug", "1")
                .body(jsonFrom("auction/lifestreet/test-auction-lifestreet-request.json"))
                .post("/auction");

        // then
        assertThat(response.header("Cache-Control")).isEqualTo("no-cache, no-store, must-revalidate");
        assertThat(response.header("Pragma")).isEqualTo("no-cache");
        assertThat(response.header("Expires")).isEqualTo("0");
        assertThat(response.header("Access-Control-Allow-Credentials")).isEqualTo("true");
        assertThat(response.header("Access-Control-Allow-Origin")).isEqualTo("http://www.example.com");

        final String expectedAuctionResponse = legacyAuctionResponseFrom(
                "auction/lifestreet/test-auction-lifestreet-response.json",
                response, singletonList(LIFESTREET));
        assertThat(response.asString()).isEqualTo(expectedAuctionResponse);
    }
}
