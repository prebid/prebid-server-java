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
public class PangleTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromPangle() throws IOException, JSONException {
        // given
        // Pangle bid response for imp 001
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/pangle-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/pangle/test-pangle-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/pangle/test-pangle-bid-response-1.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToBidCacheRequest(
                        jsonFrom("openrtb2/pangle/test-cache-pangle-request.json")))
                .willReturn(aResponse()
                        .withTransformers("cache-response-transformer")
                        .withTransformerParameter("matcherName", "openrtb2/pangle/test-cache-matcher-pangle.json")));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"pangle":"PG-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7InBhbmdsZSI6IlBHLVVJRCJ9fQ==")
                .body(jsonFrom("openrtb2/pangle/test-auction-pangle-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/pangle/test-auction-pangle-response.json",
                response, singletonList("pangle"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
