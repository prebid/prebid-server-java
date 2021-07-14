package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.skyscreamer.jsonassert.Customization;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToIgnoreCase;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;
import static java.util.Collections.singletonList;

@RunWith(SpringRunner.class)
public class SharethroughTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromSharethrough() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/sharethrough-exchange"))
                .withQueryParam("placement_key", equalTo("abc123"))
                .withQueryParam("bidId", equalTo("bid"))
                .withQueryParam("consent_required", equalTo("false"))
                .withQueryParam("consent_string", equalTo("consentValue"))
                .withQueryParam("us_privacy", equalTo("1NYN"))
                .withQueryParam("instant_play_capable", equalTo("true"))
                .withQueryParam("stayInIframe", equalTo("true"))
                .withQueryParam("height", equalTo("50"))
                .withQueryParam("width", equalTo("50"))
                .withQueryParam("supplyId", equalTo("FGMrCMMc"))
                .withQueryParam("adRequestAt", notEmpty())
                .withQueryParam("ttduid", equalTo("id"))
                .withQueryParam("strVersion", equalTo("8"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=utf-8"))
                .withHeader("User-Agent", equalTo("Android Chrome/60"))
                .withHeader("X-Forwarded-For", equalTo("193.168.244.1"))
                .withHeader("Origin", equalTo("http://www.example.com"))
                .withHeader("Referer", equalTo("http://www.example.com"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/sharethrough/test-sharethrough-request.json")))
                .willReturn(
                        aResponse().withBody(jsonFrom("openrtb2/sharethrough/test-sharethrough-bid-response-1.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                .body(jsonFrom("openrtb2/sharethrough/test-auction-sharethrough-request.json"))
                .post("/openrtb2/auction");

        // then
        assertJsonEquals("openrtb2/sharethrough/test-auction-sharethrough-response.json",
                response,
                singletonList("sharethrough"),
                new Customization("seatbid[*].bid[*].adm", (o1, o2) -> true));
    }
}
