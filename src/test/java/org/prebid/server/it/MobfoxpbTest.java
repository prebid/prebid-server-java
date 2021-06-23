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
public class MobfoxpbTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromMobfoxpb() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/mobfoxpb-exchange"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Content-Type", equalTo("application/json;charset=UTF-8"))
                .withQueryParam("c", equalTo("rtb"))
                .withQueryParam("m", equalTo("req"))
                .withQueryParam("key", equalTo("someKey"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/mobfoxpb/test-mobfoxpb-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/mobfoxpb/test-mobfoxpb-bid-response-1.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"mobfoxpb":"MBPB-FOX"}}
                .cookie("uids", "eyJ1aWRzIjp7Im1vYmZveHBiIjoiTUJQQi1GT1gifX0=")
                .body(jsonFrom("openrtb2/mobfoxpb/test-auction-mobfoxpb-request.json"))
                .post("/openrtb2/auction");

        // then
        final String expectedAuctionResponse = openrtbAuctionResponseFrom(
                "openrtb2/mobfoxpb/test-auction-mobfoxpb-response.json",
                response, singletonList("mobfoxpb"));

        JSONAssert.assertEquals(expectedAuctionResponse, response.asString(), JSONCompareMode.NON_EXTENSIBLE);
    }
}
