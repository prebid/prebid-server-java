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
public class ConnectAdTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromConnectAd() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/connectad-exchange"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Content-Type", equalTo("application/json;charset=UTF-8"))
                .withHeader("User-Agent", equalTo("userAgent"))
                .withHeader("Accept-Language", equalTo("en"))
                .withHeader("X-Forwarded-For", equalTo("193.168.244.1"))
                .withHeader("DNT", equalTo("2"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/connectad/test-connectad-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/connectad/test-connectad-bid-response.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"connectad":"CA-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7ImNvbm5lY3RhZCI6IkNBLVVJRCJ9fQ==")
                .body(jsonFrom("openrtb2/connectad/test-auction-connectad-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/connectad/test-auction-connectad-response.json",
                response, singletonList("connectad"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
