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
public class RubiconTest extends IntegrationTest {

    @Test
    public void testOpenrtb2AuctionCoreFunctionality() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/rubicon-exchange"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Content-Type", equalTo("application/json;charset=UTF-8"))
                .withQueryParam("tk_xint", equalTo("rp-pbs"))
                .withRequestBody(equalToJson(
                        jsonFrom("openrtb2/rubicon/test-rubicon-bid-request.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/rubicon/test-rubicon-bid-response.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToBidCacheRequest(
                        jsonFrom("openrtb2/rubicon/test-cache-rubicon-request.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/rubicon/test-cache-rubicon-response.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"rubicon":"RUB-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7InJ1Ymljb24iOiJSVUItVUlEIn19")
                .body(jsonFrom("openrtb2/rubicon/test-auction-rubicon-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/rubicon/test-auction-rubicon-response.json",
                response, singletonList("rubicon"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
