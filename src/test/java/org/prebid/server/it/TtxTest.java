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
public class TtxTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFrom33Across() throws IOException, JSONException {
        // given
        // 33Across bid response for imp 001
        wireMockRule.stubFor(post(urlPathEqualTo("/ttx-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/ttx/test-ttx-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/ttx/test-ttx-bid-response-1.json"))));

        // pre-bid cache
        wireMockRule.stubFor(post(urlPathEqualTo("/cache"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/ttx/test-cache-ttx-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/ttx/test-cache-ttx-response.json"))));

        // when
        final Response response = given(spec)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "192.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"33across":"TTX-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7IjMzYWNyb3NzIjoiVFRYLVVJRCJ9fQ==")
                .body(jsonFrom("openrtb2/ttx/test-auction-ttx-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/ttx/test-auction-ttx-response.json",
                response, singletonList("ttx"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
