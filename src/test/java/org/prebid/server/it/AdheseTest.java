package org.prebid.server.it;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

import static io.restassured.RestAssured.given;
import static java.util.Collections.singletonList;

@RunWith(SpringRunner.class)
public class AdheseTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromAdhese() throws IOException, JSONException {

        WIRE_MOCK_RULE.stubFor(WireMock.get(WireMock.urlPathEqualTo("/adhese-exchange/sl_adhese_prebid_demo_-"
                + "leaderboard/ag55/cigent;brussels/tlall/xtconsentValue/xfhttp%3A%2F%2Fwww.example.com"))
                .withHeader("Accept", WireMock.equalTo("application/json"))
                .withHeader("Content-Type", WireMock.equalTo("application/json;charset=UTF-8"))
                .withRequestBody(WireMock.absent())
                .willReturn(WireMock.aResponse().withBody(jsonFrom("openrtb2/adhese/test-adhese-bid-response.json"))));

        // pre-bid cache
        WIRE_MOCK_RULE.stubFor(WireMock.post(WireMock.urlPathEqualTo("/cache"))
                .withRequestBody(WireMock.equalToJson(jsonFrom("openrtb2/adhese/test-cache-adhese-request.json")))
                .willReturn(WireMock.aResponse()
                        .withBody(jsonFrom("openrtb2/adhese/test-cache-adhese-response.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                .cookie("uids", "eyJ1aWRzIjp7ImFkaGVzZSI6IkFILVVJRCJ9fQ==")
                .body(jsonFrom("openrtb2/adhese/test-auction-adhese-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/adhese/test-auction-adhese-response.json",
                response, singletonList("adhese"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
