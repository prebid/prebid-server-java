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
public class AdmixerTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromAdmixer() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/admixer-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/admixer/test-admixer-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/admixer/test-admixer-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/admixer/test-auction-admixer-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/admixer/test-auction-admixer-response.json", response,
                singletonList("admixer"));
    }
}
