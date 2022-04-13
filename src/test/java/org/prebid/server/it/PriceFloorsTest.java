package org.prebid.server.it;

import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.json.JSONException;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.server.VertxTest;
import org.prebid.server.model.Endpoint;
import org.skyscreamer.jsonassert.Customization;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.comparator.CustomComparator;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static io.restassured.RestAssured.given;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@RunWith(SpringRunner.class)
@TestPropertySource(
        value = {"test-application.properties"},
        properties = {"price-floors.enabled=true", "http.port=55555", "admin.port=0"}
)
public class PriceFloorsTest extends VertxTest {

    private static final int APP_PORT = 55555;
    private static final int WIREMOCK_PORT = 8090;

    private static final RequestSpecification SPEC = IntegrationTest.spec(APP_PORT);

    @ClassRule
    public static final WireMockClassRule WIRE_MOCK_RULE = new WireMockClassRule(options().port(WIREMOCK_PORT));

    @BeforeClass
    public static void setUp() throws IOException {
        WIRE_MOCK_RULE.stubFor(get(urlPathEqualTo("/periodic-update"))
                .willReturn(aResponse().withBody(jsonFrom("storedrequests/test-periodic-refresh.json"))));
        WIRE_MOCK_RULE.stubFor(get(urlPathEqualTo("/currency-rates"))
                .willReturn(aResponse().withBody(jsonFrom("currency/latest.json"))));
        WIRE_MOCK_RULE.stubFor(get(urlPathEqualTo("/floors-provider"))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/floors/provided-floors.json"))));
    }

    @Test
    public void openrtb2AuctionShouldApplyPriceFloorsForTheGenericBidder() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/generic-exchange"))
                .inScenario("Price Floors Test")
                .whenScenarioStateIs(STARTED)
                .withRequestBody(equalToJson(jsonFrom("openrtb2/floors/floors-test-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/floors/floors-test-bid-response.json")))
                .willSetStateTo("Floors from request"));

        // when

        final Response firstResponse = responseFor("openrtb2/floors/floors-test-auction-request-1.json",
                Endpoint.openrtb2_auction);

        // then
        assertJsonEquals(
                "openrtb2/floors/floors-test-auction-response.json", firstResponse, singletonList("generic"));

        // given
        final StubMapping stubMapping = WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/generic-exchange"))
                .inScenario("Price Floors Test")
                .whenScenarioStateIs("Floors from request")
                .withRequestBody(equalToJson(jsonFrom("openrtb2/floors/floors-test-bid-request-2.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/floors/floors-test-bid-response.json")))
                .willSetStateTo("Floors from provider"));

        // when
        final Response secondResponse = responseFor("openrtb2/floors/floors-test-auction-request-2.json",
                Endpoint.openrtb2_auction);

        // then
        assertThat(stubMapping.getNewScenarioState()).isEqualTo("Floors from provider");
        assertJsonEquals(
                "openrtb2/floors/floors-test-auction-response.json", secondResponse, singletonList("generic"));
    }

    protected static void assertJsonEquals(String file,
                                           Response response,
                                           List<String> bidders,
                                           Customization... customizations) throws IOException, JSONException {
        final List<Customization> fullCustomizations = new ArrayList<>(Arrays.asList(customizations));
        fullCustomizations.add(new Customization("ext.prebid.auctiontimestamp", (o1, o2) -> true));
        fullCustomizations.add(new Customization("ext.responsetimemillis.cache", (o1, o2) -> true));
        String expectedRequest = jsonFrom(file);
        for (String bidder : bidders) {
            expectedRequest = replaceBidderRelatedStaticInfo(expectedRequest, bidder);
            fullCustomizations.add(new Customization(
                    String.format("ext.responsetimemillis.%s", bidder), (o1, o2) -> true));
        }

        JSONAssert.assertEquals(expectedRequest, response.asString(),
                new CustomComparator(JSONCompareMode.NON_EXTENSIBLE,
                        fullCustomizations.toArray(new Customization[0])));
    }

    private static String replaceBidderRelatedStaticInfo(String json, String bidder) {

        return json.replaceAll("\\{\\{ " + bidder + "\\.exchange_uri }}",
                "http://localhost:" + WIREMOCK_PORT + "/" + bidder + "-exchange");
    }

    private static Response responseFor(String file, Endpoint endpoint) throws IOException {
        return given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                .body(jsonFrom(file))
                .post(endpoint.value());
    }

    private static String jsonFrom(String file) throws IOException {
        // workaround to clear formatting
        return mapper.writeValueAsString(mapper.readTree(IntegrationTest.class.getResourceAsStream(file)));
    }
}
