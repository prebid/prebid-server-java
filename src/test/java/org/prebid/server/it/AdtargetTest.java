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
import static java.util.Collections.singletonList;

@RunWith(SpringRunner.class)
public class AdtargetTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromAdtarget() throws IOException, JSONException {
        // given
        // Adtarget bid response for imp 14
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/adtarget-exchange"))
                .withQueryParam("aid", equalTo("1000"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=UTF-8"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/adtarget/test-adtarget-bid-request-1.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/adtarget/test-adtarget-bid-response-1.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/adtarget/test-cache-adtarget-request.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/adtarget/test-cache-adtarget-response.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"adtarget":"AD-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7ImFkdGFyZ2V0IjoiQUQtVUlEIn19")
                .body(jsonFrom("openrtb2/adtarget/test-auction-adtarget-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/adtarget/test-auction-adtarget-response.json",
                response, singletonList("adtarget"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
