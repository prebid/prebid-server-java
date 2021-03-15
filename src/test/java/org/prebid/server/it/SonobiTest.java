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
public class SonobiTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromSonobi() throws IOException, JSONException {
        // given
        // Sonobi bid response for imp 001
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/sonobi-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/sonobi/test-sonobi-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/sonobi/test-sonobi-bid-response-1.json"))));

        // Sonobi bid response for imp 002
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/sonobi-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/sonobi/test-sonobi-bid-request-2.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/sonobi/test-sonobi-bid-response-2.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToBidCacheRequest(
                        jsonFrom("openrtb2/sonobi/test-cache-sonobi-request.json")))
                .willReturn(aResponse().withTransformers("cache-response-transformer")
                        .withTransformerParameter("matcherName", "openrtb2/sonobi/test-cache-matcher-sonobi.json")));
        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"sonobi":"SB-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7InNvbm9iaSI6IlNCLVVJRCJ9fQ==")
                .body(jsonFrom("openrtb2/sonobi/test-auction-sonobi-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/sonobi/test-auction-sonobi-response.json",
                response, singletonList("sonobi"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
