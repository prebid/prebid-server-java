package org.prebid.server.it;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.json.JSONException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.prebid.server.model.Endpoint;
import org.prebid.server.util.IntegrationTestsUtil;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.prebid.server.util.IntegrationTestsUtil.assertJsonEquals;
import static org.prebid.server.util.IntegrationTestsUtil.jsonFrom;
import static org.prebid.server.util.IntegrationTestsUtil.responseFor;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(
        locations = {"test-application.properties"},
        properties = {
                "price-floors.enabled=true",
                "server.http.port=8050",
                "admin.port=0",
                "settings.in-memory-cache.http-update.endpoint=http://localhost:8100/periodic-update",
                "settings.in-memory-cache.http-update.amp-endpoint=http://localhost:8100/periodic-update",
                "currency-converter.external-rates.url=http://localhost:8100/currency-rates",
                "adapters.generic.endpoint=http://localhost:8100/generic-exchange"
        })
public class PriceFloorsTest {

    private static final int APP_PORT = 8050;
    private static final int WIREMOCK_PORT = 8100;

    @SuppressWarnings("unchecked")
    @RegisterExtension
    public static final WireMockExtension WIRE_MOCK_RULE = WireMockExtension.newInstance()
            .options(wireMockConfig()
                    .port(WIREMOCK_PORT)
                    .gzipDisabled(true)
                    .jettyStopTimeout(5000L)
                    .extensions(IntegrationTest.CacheResponseTransformer.class))
            .build();

    private static final String PRICE_FLOORS = "Price Floors Test";
    private static final String FLOORS_FROM_REQUEST = "Floors from request";
    private static final String FLOORS_FROM_PROVIDER = "Floors from provider";

    private static final RequestSpecification SPEC = IntegrationTest.spec(APP_PORT);

    @BeforeAll
    public static void beforeAll() throws IOException {
        WIRE_MOCK_RULE.stubFor(get(urlPathEqualTo("/periodic-update"))
                .willReturn(aResponse().withBody(jsonFrom("storedrequests/test-periodic-refresh.json"))));
        WIRE_MOCK_RULE.stubFor(get(urlPathEqualTo("/currency-rates"))
                .willReturn(aResponse().withBody(jsonFrom("currency/latest.json"))));
    }

    @BeforeEach
    public void setUp() throws IOException {
        beforeAll();
    }

    @AfterEach
    public void resetWireMock() {
        WIRE_MOCK_RULE.resetAll();
    }

    @Test
    public void openrtb2AuctionShouldApplyPriceFloorsForTheGenericBidder() throws IOException, JSONException {
        // given
        WIRE_MOCK_RULE.stubFor(get(urlPathEqualTo("/floors-provider"))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/floors/provided-floors.json"))));

        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/generic-exchange"))
                .inScenario(PRICE_FLOORS)
                .whenScenarioStateIs(STARTED)
                .withRequestBody(equalToJson(jsonFrom("openrtb2/floors/floors-test-bid-request-1.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/floors/floors-test-bid-response.json")))
                .willSetStateTo(FLOORS_FROM_REQUEST));

        // when
        final Response firstResponse = responseFor(
                "openrtb2/floors/floors-test-auction-request-1.json",
                Endpoint.openrtb2_auction,
                SPEC);

        // then
        assertJsonEquals(
                "openrtb2/floors/floors-test-auction-response.json",
                firstResponse,
                singletonList("generic"),
                PriceFloorsTest::replaceBidderRelatedStaticInfo);

        // given
        final StubMapping stubMapping = WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/generic-exchange"))
                .inScenario(PRICE_FLOORS)
                .whenScenarioStateIs(FLOORS_FROM_REQUEST)
                .withRequestBody(equalToJson(jsonFrom("openrtb2/floors/floors-test-bid-request-2.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/floors/floors-test-bid-response.json")))
                .willSetStateTo(FLOORS_FROM_PROVIDER));

        // when
        final Response secondResponse = responseFor(
                "openrtb2/floors/floors-test-auction-request-2.json",
                Endpoint.openrtb2_auction,
                SPEC);

        // then
        assertThat(stubMapping.getNewScenarioState()).isEqualTo(FLOORS_FROM_PROVIDER);
        assertJsonEquals(
                "openrtb2/floors/floors-test-auction-response.json",
                secondResponse,
                singletonList("generic"),
                PriceFloorsTest::replaceBidderRelatedStaticInfo);
    }

    @Test
    public void openrtb2AuctionShouldSkipPriceFloorsForTheGenericBidderWhenGenericIsInNoSignalBiddersList()
            throws IOException, JSONException {

        WIRE_MOCK_RULE.stubFor(post(urlPathEqualTo("/generic-exchange"))
                .withRequestBody(equalToJson(jsonFrom("openrtb2/floors/floors-test-bid-request-no-signal.json")))
                .willReturn(aResponse().withBody(jsonFrom("openrtb2/floors/floors-test-bid-response.json"))));

        // when
        final Response firstResponse = responseFor(
                "openrtb2/floors/floors-test-auction-request-no-signal.json",
                Endpoint.openrtb2_auction,
                SPEC);

        // then
        assertJsonEquals(
                "openrtb2/floors/floors-test-auction-response-no-signal.json",
                firstResponse,
                singletonList("generic"),
                PriceFloorsTest::replaceBidderRelatedStaticInfo);
    }

    private static String replaceBidderRelatedStaticInfo(String json, String bidder) {
        return IntegrationTestsUtil.replaceBidderRelatedStaticInfo(json, bidder, WIREMOCK_PORT);
    }
}
