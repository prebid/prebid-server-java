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

public class CadentApertureMxTest extends IntegrationTest {

    @Test
    public void openrtb2AuctionShouldRespondWithBidsFromCadentApertureMx() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/emx_digital-exchange"))
                .withRequestBody(
                        equalToJson(jsonFrom("openrtb2/cadentaperturemx/test-cadentaperturemx-bid-request.json"),
                        true, false))
                .willReturn(aResponse().withBody(
                        jsonFrom("openrtb2/cadentaperturemx/test-cadentaperturemx-bid-response.json"))));

        // when
        final Response response = responseFor(
                "openrtb2/cadentaperturemx/test-auction-cadentaperturemx-request.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals("openrtb2/cadentaperturemx/test-auction-cadentaperturemx-response.json",
                response, singletonList("cadent_aperture_mx"));
    }
}
