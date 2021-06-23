package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.restassured.RestAssured.given;
import static java.util.Collections.singletonList;

@RunWith(SpringRunner.class)
public class SomoaudienceTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromSomoaudience() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/somoaudience-exchange"))
                .withQueryParam("s", equalTo("placementId02"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Content-Type", equalTo("application/json;charset=UTF-8"))
                .withHeader("x-openrtb-version", equalTo("2.5"))
                .withHeader("User-Agent", equalTo("userAgent"))
                .withHeader("X-Forwarded-For", equalTo("193.168.244.1"))
                .withHeader("Accept-Language", equalTo("en"))
                .withHeader("DNT", equalTo("2"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/somoaudience/test-somoaudience-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom(
                        "openrtb2/somoaudience/test-somoaudience-bid-response-1.json"))));

        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/somoaudience-exchange"))
                .withQueryParam("s", equalTo("placementId03"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Content-Type", equalTo("application/json;charset=UTF-8"))
                .withHeader("x-openrtb-version", equalTo("2.5"))
                .withHeader("User-Agent", equalTo("userAgent"))
                .withHeader("X-Forwarded-For", equalTo("193.168.244.1"))
                .withHeader("Accept-Language", equalTo("en"))
                .withHeader("DNT", equalTo("2"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/somoaudience/test-somoaudience-bid-request-2.json")))
                .willReturn(aResponse().withBody(jsonFrom(
                        "openrtb2/somoaudience/test-somoaudience-bid-response-2.json"))));

        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/somoaudience-exchange"))
                .withQueryParam("s", equalTo("placementId04"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Content-Type", equalTo("application/json;charset=UTF-8"))
                .withHeader("x-openrtb-version", equalTo("2.5"))
                .withHeader("User-Agent", equalTo("userAgent"))
                .withHeader("X-Forwarded-For", equalTo("193.168.244.1"))
                .withHeader("Accept-Language", equalTo("en"))
                .withHeader("DNT", equalTo("2"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/somoaudience/test-somoaudience-bid-request-3.json")))
                .willReturn(aResponse().withBody(jsonFrom(
                        "openrtb2/somoaudience/test-somoaudience-bid-response-3.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"somoaudience":"SM-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7InNvbW9hdWRpZW5jZSI6IlNNLVVJRCJ9fQ==")
                .body(jsonFrom("openrtb2/somoaudience/test-auction-somoaudience-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/somoaudience/test-auction-somoaudience-response.json",
                response, singletonList("somoaudience"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
