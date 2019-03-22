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
public class FacebookTest extends IntegrationTest {

    private static final String FACEBOOK = "audienceNetwork";

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromFacebook() throws IOException, JSONException {
        // given
        // facebook bid response for imp 5
        wireMockRule.stubFor(post(urlPathEqualTo("/audienceNetwork-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/facebook/test-facebook-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/facebook/test-facebook-bid-response-1.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/facebook/test-cache-facebook-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/facebook/test-cache-facebook-response.json"))));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "192.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"audienceNetwork":"FB-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7ImF1ZGllbmNlTmV0d29yayI6IkZCLVVJRCJ9fQ==")
                .body(jsonFrom("openrtb2/facebook/test-auction-facebook-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/facebook/test-auction-facebook-response.json",
                response, singletonList(FACEBOOK));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    public void auctionShouldRespondWithBidsFromFacebook() throws IOException {
        // given
        // facebook bid response for ad unit 5
        wireMockRule.stubFor(post(urlPathEqualTo("/audienceNetwork-exchange"))
                .withRequestBody(equalToJson(jsonFrom("auction/facebook/test-facebook-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("auction/facebook/test-facebook-bid-response-1.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("auction/facebook/test-cache-facebook-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("auction/facebook/test-cache-facebook-response.json"))));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "192.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                //this uids cookie value stands for {"uids":{"audienceNetwork":"FB-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7ImF1ZGllbmNlTmV0d29yayI6IkZCLVVJRCJ9fQ==")
                .queryParam("debug", "1")
                .body(jsonFrom("auction/facebook/test-auction-facebook-request.json"))
                .post("/auction");

        // then
        assertThat(response.header("Cache-Control")).isEqualTo("no-cache, no-store, must-revalidate");
        assertThat(response.header("Pragma")).isEqualTo("no-cache");
        assertThat(response.header("Expires")).isEqualTo("0");
        assertThat(response.header("Access-Control-Allow-Credentials")).isEqualTo("true");
        assertThat(response.header("Access-Control-Allow-Origin")).isEqualTo("http://www.example.com");

        final String expectedAuctionResponse = legacyAuctionResponseFrom(
                "auction/facebook/test-auction-facebook-response.json",
                response, singletonList(FACEBOOK));
        assertThat(response.asString()).isEqualTo(expectedAuctionResponse);
    }
}
