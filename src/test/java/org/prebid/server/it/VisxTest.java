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
public class VisxTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromVisx() throws IOException, JSONException {
        // given
        // VisxTest bid response for imp 001
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/visx-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/visx/test-visx-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/visx/test-visx-bid-response.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/visx/test-cache-visx-request.json"), true, false))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/visx/test-cache-visx-response.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"visx":"VISX-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7InZpc3giOiJWSVNYLVVJRCJ9fQ==")
                .body(jsonFrom("openrtb2/visx/test-auction-visx-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/visx/test-auction-visx-response.json",
                response, singletonList("visx"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
