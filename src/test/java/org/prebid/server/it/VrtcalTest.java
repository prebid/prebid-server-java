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

@RunWith(SpringRunner.class)
public class VrtcalTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromVrtcal() throws IOException, JSONException {
        // given
        // Vrtcal bid response for imp 001
        wireMockRule.stubFor(post(urlPathEqualTo("/vrtcal-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/vrtcal/test-vrtcal-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/vrtcal/test-vrtcal-bid-response-1.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/vrtcal/test-cache-vrtcal-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/vrtcal/test-cache-vrtcal-response.json"))));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "192.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"vrtcal":"VR-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7InZydGNhbCI6IlZSLVVJRCJ9fQ==")
                .body(jsonFrom("openrtb2/vrtcal/test-auction-vrtcal-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/vrtcal/test-auction-vrtcal-response.json",
                response, singletonList("vrtcal"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
