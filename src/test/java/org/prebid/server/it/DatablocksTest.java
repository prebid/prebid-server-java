package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.skyscreamer.jsonassert.JSONAssert;
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
public class DatablocksTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromDatablocks() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/datablocks-exchange"))
                .withQueryParam("sid", equalTo("1"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/datablocks/test-datablocks-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/datablocks/test-datablocks-bid-response-1.json"))));

        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/datablocks-exchange"))
                .withQueryParam("sid", equalTo("2"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/datablocks/test-datablocks-bid-request-2.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/datablocks/test-datablocks-bid-response-2.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"datablocks":"DB-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7ImRhdGFibG9ja3MiOiJEQi1VSUQifX0=")
                .body(jsonFrom("openrtb2/datablocks/test-auction-datablocks-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/datablocks/test-auction-datablocks-response.json",
                response, singletonList("datablocks"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), openrtbCacheDebugComparator());
    }
}
