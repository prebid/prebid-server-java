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
public class EngagebdrTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromEngagebdr() throws IOException, JSONException {
        // given
        // engagebdr bid response for imp 021
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/engagebdr-exchange"))
                .withQueryParam("zoneid", equalTo("99999"))
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=UTF-8"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/engagebdr/test-engagebdr-bid-request-1.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/engagebdr/test-engagebdr-bid-response-1.json"))));

        // engagebdr bid response for imp 022
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/engagebdr-exchange"))
                .withQueryParam("zoneid", equalTo("88888"))
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=UTF-8"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/engagebdr/test-engagebdr-bid-request-2.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/engagebdr/test-engagebdr-bid-response-2.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToBidCacheRequest(
                        jsonFrom("openrtb2/engagebdr/test-cache-engagebdr-request.json")))
                .willReturn(aResponse().withTransformers("cache-response-transformer")
                        .withTransformerParameter("matcherName",
                                "openrtb2/engagebdr/test-cache-matcher-engagebdr.json")));
        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"engagebdr":"EG-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7ImVuZ2FnZWJkciI6IkVHLVVJRCJ9fQ==")
                .body(jsonFrom("openrtb2/engagebdr/test-auction-engagebdr-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/engagebdr/test-auction-engagebdr-response.json",
                response, singletonList("engagebdr"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}

