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
public class YeahmobiTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromYeahmobi() throws IOException, JSONException {
        // given
        // YeahmobiBidder bid response for imp 001
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/yeahmobi-exchange"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Content-Type", equalTo("application/json;charset=UTF-8"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/yeahmobi/test-yeahmobi-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/yeahmobi/test-yeahmobi-bid-response.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/yeahmobi/test-cache-yeahmobi-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/yeahmobi/test-cache-yeahmobi-response.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"yeahmobi":"YM-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7InllYWhtb2JpIjoiWU0tVUlEIn19")
                .body(jsonFrom("openrtb2/yeahmobi/test-auction-yeahmobi-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/yeahmobi/test-auction-yeahmobi-response.json",
                response, singletonList("yeahmobi"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
