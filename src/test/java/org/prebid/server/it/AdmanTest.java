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
public class AdmanTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromAdman() throws IOException, JSONException {
        // given
        // Adman bid response for imp 001
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/adman-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/adman/test-adman-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/adman/test-adman-bid-response-1.json"))));

        // Adman bid response for imp 002
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/adman-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/adman/test-adman-bid-request-2.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/adman/test-adman-bid-response-2.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToBidCacheRequest(
                        jsonFrom("openrtb2/adman/test-cache-adman-request.json")))
                .willReturn(aResponse()
                        .withTransformers("cache-response-transformer")
                        .withTransformerParameter("matcherName", "openrtb2/adman/test-cache-matcher-adman.json")));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"adman":"AD-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7ImFkbWFuIjoiQUQtVUlEIn19")
                .body(jsonFrom("openrtb2/adman/test-auction-adman-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/adman/test-auction-adman-response.json",
                response, singletonList("adman"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
