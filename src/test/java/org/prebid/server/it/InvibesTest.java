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
public class InvibesTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromInvibes() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/invibes-exchange"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Content-Type", equalTo("application/json;charset=UTF-8"))
                .withHeader("Referer", equalTo("http://www.example.com"))
                .withHeader("Aver", equalTo("prebid_1.0.0"))
                .withHeader("X-Forwarded-For", equalTo("193.168.244.1"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/invibes/test-invibes-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/invibes/test-invibes-bid-response.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"invibes":"IV-UID"}}
                .cookie("uids", "eyJ1aWRzIjp7ImludmliZXMiOiJJVi1VSUQifX0=")
                .body(jsonFrom("openrtb2/invibes/test-auction-invibes-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/invibes/test-auction-invibes-response.json",
                response, singletonList("invibes"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
