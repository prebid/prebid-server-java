package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.skyscreamer.jsonassert.JSONAssert;
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
public class SmartadserverTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromSmartadserver() throws IOException, JSONException {
        // given
        // Smartadserver bid response for imp 001
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/smartadserver-exchange/api/bid"))
                .withQueryParam("callerId", equalTo("5"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/smartadserver/test-smartadserver-bid-request-1.json")))
                .willReturn(aResponse()
                        .withBody(jsonFrom("openrtb2/smartadserver/test-smartadserver-bid-response-1.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/smartadserver/test-cache-smartadserver-request.json")))
                .willReturn(aResponse()
                        .withBody(jsonFrom("openrtb2/smartadserver/test-cache-smartadserver-response.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"smartadserver":"SA-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7InNtYXJ0YWRzZXJ2ZXIiOiJTQS1VSUQifX0=")
                .body(jsonFrom("openrtb2/smartadserver/test-auction-smartadserver-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/smartadserver/test-auction-smartadserver-response.json",
                response, singletonList("smartadserver"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), openrtbCacheDebugComparator());
    }
}
