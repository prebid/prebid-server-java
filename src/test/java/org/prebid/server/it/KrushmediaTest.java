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
public class KrushmediaTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromKrushmedia() throws IOException, JSONException {
        // given
        // Krushmedia bid response for imp
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/krushmedia-exchange"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=UTF-8"))
                .withHeader("User-Agent", equalToIgnoreCase("test-user-agent"))
                .withHeader("X-Forwarded-For", equalToIgnoreCase("123.123.123.123"))
                .withHeader("Accept-Language", equalToIgnoreCase("en"))
                .withHeader("Dnt", equalToIgnoreCase("0"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/krushmedia/test-krushmedia-bid-request.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/krushmedia/test-krushmedia-bid-response.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/krushmedia/test-cache-krushmedia-request.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/krushmedia/test-cache-krushmedia-response.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"krushmedia":"KM-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7ImtydXNobWVkaWEiOiJLTS1VSUQifX0=")
                .body(jsonFrom("openrtb2/krushmedia/test-auction-krushmedia-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/krushmedia/test-auction-krushmedia-response.json",
                response, singletonList("krushmedia"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
