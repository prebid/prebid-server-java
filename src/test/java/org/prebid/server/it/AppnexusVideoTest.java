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
import static com.github.tomakehurst.wiremock.client.WireMock.equalToIgnoreCase;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;

@RunWith(SpringRunner.class)
public class AppnexusVideoTest extends IntegrationTest {

    @Test
    public void openrtb2VideoShouldRespondWithBidsFromAppnexus() throws IOException, JSONException {
        // given
        wireMockRule.stubFor(post(urlPathEqualTo("/appnexus-exchange"))
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=UTF-8"))
                .withHeader("Accept", equalTo("application/json"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/video/test-video-appnexus-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/video/test-video-appnexus-bid-response-1.json"))));

        wireMockRule.stubFor(post(urlPathEqualTo("/appnexus-exchange"))
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=UTF-8"))
                .withHeader("Accept", equalTo("application/json"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/video/test-video-appnexus-bid-request-2.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/video/test-video-appnexus-bid-response-2.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/video/test-cache-video-appnexus-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/video/test-cache-video-appnexus-response.json"))));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                .body(jsonFrom("openrtb2/video/test-video-appnexus-request.json"))
                .post("/openrtb2/video");

        // then
        final String expectedAuctionResponse = jsonFrom("openrtb2/video/test-video-appnexus-response.json");

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}

