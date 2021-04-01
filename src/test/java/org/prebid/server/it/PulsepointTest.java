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
public class PulsepointTest extends IntegrationTest {

    private static final String PULSEPOINT = "pulsepoint";

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromPulsepoint() throws IOException, JSONException {
        // given
        // pulsepoint bid response for imp 8
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/pulsepoint-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/pulsepoint/test-pulsepoint-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/pulsepoint/test-pulsepoint-bid-response-1.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToBidCacheRequest(
                        jsonFrom("openrtb2/pulsepoint/test-cache-pulsepoint-request.json")))
                .willReturn(aResponse().withTransformers("cache-response-transformer")
                        .withTransformerParameter("matcherName",
                                "openrtb2/pulsepoint/test-cache-matcher-pulsepoint.json")));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"pulsepoint":"PP-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7InB1bHNlcG9pbnQiOiJQUC1VSUQifX0=")
                .body(jsonFrom("openrtb2/pulsepoint/test-auction-pulsepoint-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/pulsepoint/test-auction-pulsepoint-response.json",
                response, singletonList(PULSEPOINT));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
