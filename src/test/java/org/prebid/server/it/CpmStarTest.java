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
public class CpmStarTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromCPMStar() throws IOException, JSONException {
        // given
        // Cpmstar bid response
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cpmstar-exchange"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Content-Type", equalTo("application/json;charset=UTF-8"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/cpmstar/test-cpmstar-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/cpmstar/test-cpmstar-bid-response-1.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/cpmstar/test-cache-cpmstar-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/cpmstar/test-cache-cpmstar-response.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                .cookie("uids", "eyJ1aWRzIjp7ImNwbXN0YXIiOiJDUy1VSUQifX0=")
                .body(jsonFrom("openrtb2/cpmstar/test-auction-cpmstar-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/cpmstar/test-auction-cpmstar-response.json",
                response, singletonList("cpmstar"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}

