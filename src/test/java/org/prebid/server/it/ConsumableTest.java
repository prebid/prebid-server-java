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
public class ConsumableTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromConsumable() throws IOException, JSONException {
        // given
        // consumable bid response for imp 001
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/consumable-exchange"))
                .withHeader("Cookie", equalTo("azk=CS-UID"))
                .withHeader("Origin", equalTo("http://www.example.com"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("User-Agent", equalTo("userAgent"))
                .withHeader("Forwarded", equalTo("for=193.168.244.1"))
                .withHeader("Referer", equalTo("http://www.example.com"))
                .withHeader("X-Forwarded-For", equalTo("193.168.244.1"))
                .withHeader("Host", equalTo("localhost:8090"))
                .withHeader("Content-Length", equalTo("302"))
                .withHeader("Content-Type", equalTo("application/json;charset=UTF-8"))
                .withCookie("azk", equalTo("CS-UID"))
                // The "time" field in consumable bid request is not being checked as its value is Instance.now()
                .withRequestBody(equalToJson(jsonFrom("openrtb2/consumable/test-consumable-bid-request-1.json"),
                        true, true))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/consumable/test-consumable-bid-response-1.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/consumable/test-cache-consumable-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/consumable/test-cache-consumable-response.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"consumable":"CS-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7ImNvbnN1bWFibGUiOiJDUy1VSUQifX0=")
                .body(jsonFrom("openrtb2/consumable/test-auction-consumable-request.json"))
                .post("/openrtb2/auction");

        final int indexOfTime = response.asString().indexOf("\\\"time\\\"");
        final String timeMs = response.asString().substring(indexOfTime + 9, indexOfTime + 19);

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/consumable/test-auction-consumable-response.json",
                response, singletonList("consumable")).replaceAll("\\{\\{time}}", timeMs);

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
