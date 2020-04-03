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
public class GridTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromTheMediaGrid() throws IOException, JSONException {
        // given
        // TheMediaGrid bid response for imp 001
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/grid-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/grid/test-grid-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/grid/test-grid-bid-response-1.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/grid/test-cache-grid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/grid/test-cache-grid-response.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"grid":"GRID-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7ImdyaWQiOiJHUklELVVJRCJ9fQ==")
                .body(jsonFrom("openrtb2/grid/test-auction-grid-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/grid/test-auction-grid-response.json",
                response, singletonList("grid"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
