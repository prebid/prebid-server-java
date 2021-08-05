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
public class EpomTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromTheMediaEpom() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/epom-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/epom/test-epom-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/epom/test-epom-bid-response-1.json"))));

        // when
        final Response response = responseFor("openrtb2/epom/test-auction-epom-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/epom/test-auction-epom-response.json", response, singletonList("epom"));
    }
}
