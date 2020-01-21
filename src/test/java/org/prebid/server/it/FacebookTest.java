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
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;
import static java.util.Collections.singletonList;

@RunWith(SpringRunner.class)
public class FacebookTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromFacebook() throws IOException, JSONException {
        // given
        // facebook bid response for impId001
        wireMockRule.stubFor(post(urlPathEqualTo("/audienceNetwork-exchange"))
                .withHeader("X-Fb-Pool-Routing-Token", equalTo("FB-UID"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/facebook/test-facebook-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/facebook/test-facebook-bid-response-1.json"))));

        // facebook bid response for impId002
        wireMockRule.stubFor(post(urlPathEqualTo("/audienceNetwork-exchange"))
                .withHeader("X-Fb-Pool-Routing-Token", equalTo("FB-UID"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/facebook/test-facebook-bid-request-2.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/facebook/test-facebook-bid-response-2.json"))));

        // facebook bid response for impId003
        wireMockRule.stubFor(post(urlPathEqualTo("/audienceNetwork-exchange"))
                .withHeader("X-Fb-Pool-Routing-Token", equalTo("FB-UID"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/facebook/test-facebook-bid-request-3.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/facebook/test-facebook-bid-response-3.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/facebook/test-cache-facebook-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/facebook/test-cache-facebook-response.json"))));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"audienceNetwork":"FB-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7ImF1ZGllbmNlTmV0d29yayI6IkZCLVVJRCJ9fQ==")
                .body(jsonFrom("openrtb2/facebook/test-auction-facebook-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/facebook/test-auction-facebook-response.json",
                response, singletonList("audienceNetwork"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
