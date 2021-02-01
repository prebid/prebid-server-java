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
public class MediafuseTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFrommediafuse() throws IOException, JSONException {
        // given
        // mediafuse bid response for imp 14
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/mediafuse-exchange"))
                .withQueryParam("aid", equalTo("1000"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/mediafuse/test-mediafuse-bid-request-1.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/mediafuse/test-mediafuse-bid-response-1.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/mediafuse/test-cache-mediafuse-request.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/mediafuse/test-cache-mediafuse-response.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"mediafuse":"MF-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7Im1lZGlhZnVzZSI6Ik1GLVVJRCJ9fQ==")
                .body(jsonFrom("openrtb2/mediafuse/test-auction-mediafuse-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/mediafuse/test-auction-mediafuse-response.json",
                response, singletonList("mediafuse"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
