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
import static java.util.Collections.singletonList;

public class IndicueTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromIndicue() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/indicue-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/indicue/test-indicue-bid-request.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/indicue/test-indicue-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/indicue/test-auction-indicue-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/indicue/test-auction-indicue-response.json", response,
                singletonList("indicue"));
    }
}
