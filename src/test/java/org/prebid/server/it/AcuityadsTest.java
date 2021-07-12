package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.server.model.Endpoint;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToIgnoreCase;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.Collections.singletonList;

@RunWith(SpringRunner.class)
public class AcuityadsTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromAcuityAds() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/acuityads-exchange"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Content-Type", equalToIgnoreCase("application/json;charset=UTF-8"))
                .withHeader("User-Agent", equalToIgnoreCase("userAgent"))
                .withHeader("X-Forwarded-For", equalToIgnoreCase("193.168.244.1"))
                .withHeader("X-Openrtb-Version", equalToIgnoreCase("2.5"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/acuityads/test-acuityads-bid-request.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/acuityads/test-acuityads-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/acuityads/test-auction-acuityads-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/acuityads/test-auction-acuityads-response.json", response,
                singletonList("acuityads"));
    }
}
