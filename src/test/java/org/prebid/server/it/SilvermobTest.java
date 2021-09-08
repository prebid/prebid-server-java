package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.server.model.Endpoint;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.Collections.singletonList;

@RunWith(SpringRunner.class)
public class SilvermobTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromSilvermob() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/silvermob-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/silvermob/test-silvermob-bid-request.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/silvermob/test-silvermob-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/silvermob/test-auction-silvermob-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/silvermob/test-auction-silvermob-response.json", response,
                singletonList("silvermob"));
    }
}
