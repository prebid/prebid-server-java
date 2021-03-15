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
public class ApplogyTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromApplogy() throws IOException, JSONException {
        // given
        // Applogy bid response for imp 001
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/applogy-exchange/1234"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Content-Type", equalTo("application/json;charset=UTF-8"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/applogy/test-applogy-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/applogy/test-applogy-bid-response-1.json"))));

        // Applogy bid response for imp 002
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/applogy-exchange/12345"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Content-Type", equalTo("application/json;charset=UTF-8"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/applogy/test-applogy-bid-request-2.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/applogy/test-applogy-bid-response-2.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToBidCacheRequest(
                        jsonFrom("openrtb2/applogy/test-cache-applogy-request.json")))
                .willReturn(aResponse().withTransformers("cache-response-transformer")
                        .withTransformerParameter("matcherName", "openrtb2/applogy/test-cache-matcher-applogy.json")));
        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                .cookie("uids", "eyJ1aWRzIjp7ImdhbW9zaGkiOiJHTS1VSUQifX0=")
                .body(jsonFrom("openrtb2/applogy/test-auction-applogy-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/applogy/test-auction-applogy-response.json",
                response, singletonList("applogy"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
