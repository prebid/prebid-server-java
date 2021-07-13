package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;
import static java.util.Collections.singletonList;

@RunWith(SpringRunner.class)
public class TripleliftNativeTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromTriplelift() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/triplelift_native-exchange"))
                .withRequestBody(equalToJson(
                        jsonFrom("openrtb2/tripleliftnative/test-triplelift-native-bid-request.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/tripleliftnative/test-triplelift-native-bid-response.json"))));

        // when
        final Response response = given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                // this uids cookie value stands for {"uids":{"triplelift_native":"T"}}
                .cookie("uids", "eyJ1aWRzIjp7InRyaXBsZWxpZnRfbmF0aXZlIjoiVCJ9fQ==")
                .body(jsonFrom("openrtb2/tripleliftnative/test-auction-triplelift-native-request.json"))
                .post("/openrtb2/auction");

        // then
        assertJsonEquals("openrtb2/tripleliftnative/test-auction-triplelift-native-response.json",
                response, singletonList("triplelift_native"));
    }
}

