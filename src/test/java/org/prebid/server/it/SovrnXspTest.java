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

@RunWith(SpringRunner.class)
public class SovrnXspTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromSovrnXsp() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/sovrnxsp-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/sovrnxsp/test-sovrnxsp-bid-request.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/sovrnxsp/test-sovrnxsp-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/sovrnxsp/test-auction-sovrnxsp-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/sovrnxsp/test-auction-sovrnxsp-response.json", response, singletonList("sovrnXsp"));
    }
}
