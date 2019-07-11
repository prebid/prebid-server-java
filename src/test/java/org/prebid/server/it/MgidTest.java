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
public class MgidTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromTheMgid() throws IOException, JSONException {
        // given
        wireMockRule.stubFor(post(urlPathEqualTo("/mgid-exchange/123"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/mgid/test-mgid-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/mgid/test-mgid-bid-response.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/mgid/test-cache-mgid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/mgid/test-cache-mgid-response.json"))));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "192.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"mgid":"MGID-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7Im1naWQiOiJNR0lELVVJRCJ9fQ==")
                .body(jsonFrom("openrtb2/mgid/test-auction-mgid-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/mgid/test-auction-mgid-response.json",
                response, singletonList("mgid"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
