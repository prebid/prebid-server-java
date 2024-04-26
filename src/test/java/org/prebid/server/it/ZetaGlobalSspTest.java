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

public class ZetaGlobalSspTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromZetaGlobalSsp() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/zeta_global_ssp-exchange"))
                .withRequestBody(equalToJson(
                        jsonFrom("openrtb2/zeta_global_ssp/test-zeta_global_ssp-bid-request.json")))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/zeta_global_ssp/test-zeta_global_ssp-bid-response.json"))));

        // when
        final Response response = responseFor("openrtb2/zeta_global_ssp/test-auction-zeta_global_ssp-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/zeta_global_ssp/test-auction-zeta_global_ssp-response.json", response,
                singletonList("zeta_global_ssp"));
    }
}
