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
public class PubnativeTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromThePubnative() throws IOException, JSONException {
        // given
        // Pubnative bid response for imp 001
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/pubnative-exchange"))
                .withQueryParam("zoneid", equalTo("1"))
                .withQueryParam("apptoken", equalTo("4fd53a12b78af4b39835de9e449c3082"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/pubnative/test-pubnative-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/pubnative/test-pubnative-bid-response-1.json"))));

        // Pubnative bid response for imp 002
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/pubnative-exchange"))
                .withQueryParam("zoneid", equalTo("2"))
                .withQueryParam("apptoken", equalTo("4fd53a12b78af4b39835de9e449c"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/pubnative/test-pubnative-bid-request-2.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/pubnative/test-pubnative-bid-response-2.json"))));

        // Pubnative bid response for imp 003
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/pubnative-exchange"))
                .withQueryParam("zoneid", equalTo("3"))
                .withQueryParam("apptoken", equalTo("4fd53a12b78af4b39835de9e"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/pubnative/test-pubnative-bid-request-3.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/pubnative/test-pubnative-bid-response-3.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToBidCacheRequest(
                        jsonFrom("openrtb2/pubnative/test-cache-pubnative-request.json")))
                .willReturn(aResponse()
                        .withTransformers("cache-response-transformer")
                        .withTransformerParameter("matcherName",
                                "openrtb2/pubnative/test-cache-matcher-pubnative.json")));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"pubnative":"PN-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7InB1Ym5hdGl2ZSI6IlBOLVVJRCJ9fQ==")
                .body(jsonFrom("openrtb2/pubnative/test-auction-pubnative-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/pubnative/test-auction-pubnative-response.json",
                response, singletonList("pubnative"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
