package org.prebid.server.it;

import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.prebid.server.model.Endpoint;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

@RunWith(SpringRunner.class)
public class EpsilonTest extends IntegrationTest {

    private static final String EPSILON = "epsilon";
    private static final String EPSILON_ALIAS = "conversant";

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromEpsilon() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/epsilon-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/epsilon/test-epsilon-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/epsilon/test-epsilon-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/epsilon/test-auction-epsilon-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/epsilon/test-auction-epsilon-response.json", response,
                singletonList(EPSILON));
    }

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromConversant() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/epsilon-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/epsilon/alias/test-epsilon-bid-request.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/epsilon/alias/test-epsilon-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/epsilon/alias/test-auction-epsilon-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/epsilon/alias/test-auction-epsilon-response.json", response,
                asList(EPSILON, EPSILON_ALIAS));
    }
}
