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
public class InteractiveoffersTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromInteractiveoffers() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/interactiveoffers-exchange"))
                .withRequestBody(equalToJson(
                        jsonFrom("openrtb2/interactiveoffers/test-interactiveoffers-bid-request.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/interactiveoffers/test-interactiveoffers-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/interactiveoffers/test-auction-interactiveoffers-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/interactiveoffers/test-auction-interactiveoffers-response.json", response,
                singletonList("interactiveoffers"));
    }
}
