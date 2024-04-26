package org.prebid.server.it;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.restassured.response.Response;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.prebid.server.model.Endpoint;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static java.util.Collections.singletonList;

@RunWith(SpringRunner.class)
public class AdheseTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromAdhese() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(WireMock.post(WireMock.urlPathEqualTo("/adhese-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/adhese/test-adhese-bid-request.json")))
                .willReturn(WireMock.aResponse().withBody(jsonFrom("openrtb2/adhese/test-adhese-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/adhese/test-auction-adhese-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/adhese/test-auction-adhese-response.json", response,
                singletonList("adhese"));
    }
}
