package org.prebid.server.it;

import io.restassured.response.Response;
import java.io.IOException;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.test.context.junit4.SpringRunner;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;
import static java.util.Collections.singletonList;

@RunWith(SpringRunner.class)
public class SmartrtbTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromSmartrtb() throws IOException, JSONException {
        // given
        // Smartrtb bid response for imp 001
        wireMockRule.stubFor(post(urlPathEqualTo("/smartrtb-exchange/1234"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Content-Type", equalTo("application/json;charset=UTF-8"))
                .withHeader("x-openrtb-version", equalTo("2.5"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/smartrtb/test-smartrtb-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/smartrtb/test-smartrtb-bid-response.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/smartrtb/test-cache-smartrtb-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/smartrtb/test-cache-smartrtb-response.json"))));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                .cookie("uids", "eyJ1aWRzIjp7ImdhbW9zaGkiOiJHTS1VSUQifX0=")
                .body(jsonFrom("openrtb2/smartrtb/test-auction-smartrtb-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/smartrtb/test-auction-smartrtb-response.json",
                response, singletonList("smartrtb"));


        String actualStr = response.asString();
        JSONAssert.assertEquals(expectedAuctionResponse, actualStr, JSONCompareMode.NON_EXTENSIBLE);
    }
}

