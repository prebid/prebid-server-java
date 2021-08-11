package org.prebid.server.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.internal.mapping.Jackson2Mapper;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.json.JSONException;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.prebid.server.VertxTest;
import org.prebid.server.cache.proto.request.BidCacheRequest;
import org.prebid.server.cache.proto.request.PutObject;
import org.prebid.server.cache.proto.response.BidCacheResponse;
import org.prebid.server.cache.proto.response.CacheObject;
import org.prebid.server.it.hooks.TestHooksConfiguration;
import org.prebid.server.it.util.BidCacheRequestPattern;
import org.prebid.server.model.Endpoint;
import org.skyscreamer.jsonassert.ArrayValueMatcher;
import org.skyscreamer.jsonassert.Customization;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompare;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.ValueMatcher;
import org.skyscreamer.jsonassert.comparator.CustomComparator;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static io.restassured.RestAssured.given;
import static java.lang.String.format;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource({"test-application.properties", "test-application-hooks.properties"})
@Import(TestHooksConfiguration.class)
public abstract class IntegrationTest extends VertxTest {

    private static final int APP_PORT = 8080;
    private static final int WIREMOCK_PORT = 8090;
    private static final String ANY_SYMBOL_REGEX = ".*";

    @SuppressWarnings("unchecked")
    @ClassRule
    public static final WireMockClassRule WIRE_MOCK_RULE = new WireMockClassRule(options()
            .port(WIREMOCK_PORT)
            .gzipDisabled(true)
            .jettyStopTimeout(5000L)
            .extensions(IntegrationTest.CacheResponseTransformer.class));

    @Rule
    public WireMockClassRule instanceRule = WIRE_MOCK_RULE;

    protected static final RequestSpecification SPEC = spec(APP_PORT);
    private static final String HOST_AND_PORT = "localhost:" + WIREMOCK_PORT;
    private static final String CACHE_PATH = "/cache";
    private static final String CACHE_ENDPOINT = "http://" + HOST_AND_PORT + CACHE_PATH;

    @BeforeClass
    public static void setUp() throws IOException {
        WIRE_MOCK_RULE.stubFor(get(urlPathEqualTo("/periodic-update"))
                .willReturn(aResponse().withBody(jsonFrom("storedrequests/test-periodic-refresh.json"))));
        WIRE_MOCK_RULE.stubFor(get(urlPathEqualTo("/currency-rates"))
                .willReturn(aResponse().withBody(jsonFrom("currency/latest.json"))));
    }

    static RequestSpecification spec(int port) {
        return new RequestSpecBuilder()
                .setBaseUri("http://localhost")
                .setPort(port)
                .setConfig(RestAssuredConfig.config()
                        .objectMapperConfig(new ObjectMapperConfig(new Jackson2Mapper((aClass, s) -> mapper))))
                .build();
    }

    protected static Response responseFor(String file, Endpoint endpoint) throws IOException {
        return given(SPEC)
                .header("Referer", "http://www.example.com")
                .header("X-Forwarded-For", "193.168.244.1")
                .header("User-Agent", "userAgent")
                .header("Origin", "http://www.example.com")
                .body(jsonFrom(file))
                .post(endpoint.value());
    }

    protected static String jsonFrom(String file) throws IOException {
        // workaround to clear formatting
        return mapper.writeValueAsString(mapper.readTree(IntegrationTest.class.getResourceAsStream(file)));
    }

    protected static String openrtbAuctionResponseFrom(String templatePath, Response response, List<String> bidders)
            throws IOException {

        return auctionResponseFrom(templatePath, response, "ext.responsetimemillis.%s", bidders);
    }

    private static String auctionResponseFrom(String templatePath, Response response, String responseTimePath,
                                              List<String> bidders) throws IOException {

        String result = replaceStaticInfo(jsonFrom(templatePath));

        for (final String bidder : bidders) {
            result = result.replaceAll("\\{\\{ " + bidder + "\\.exchange_uri }}",
                    "http://" + HOST_AND_PORT + "/" + bidder + "-exchange");
            result = setResponseTime(response, result, bidder, responseTimePath);
        }

        return result;
    }

    private static String setResponseTime(Response response, String expectedResponseJson, String bidder,
                                          String responseTimePath) {
        final Object val = response.path(format(responseTimePath, bidder));
        final Integer responseTime = val instanceof Integer ? (Integer) val : null;
        if (responseTime != null) {
            expectedResponseJson = expectedResponseJson.replaceAll("\"\\{\\{ " + bidder + "\\.response_time_ms }}\"",
                    responseTime.toString());
        }

        final Object cacheVal = response.path("ext.responsetimemillis.cache");
        final Integer cacheResponseTime = cacheVal instanceof Integer ? (Integer) cacheVal : null;
        if (cacheResponseTime != null) {
            expectedResponseJson = expectedResponseJson.replaceAll("\"\\{\\{ cache\\.response_time_ms }}\"",
                    cacheResponseTime.toString());
        }

        return expectedResponseJson;
    }

    private static String cacheResponseFromRequestJson(
            String requestAsString, String requestCacheIdMapFile) throws IOException {

        try {
            final BidCacheRequest cacheRequest = mapper.readValue(requestAsString, BidCacheRequest.class);
            final JsonNode jsonNodeMatcher =
                    mapper.readTree(IntegrationTest.class.getResourceAsStream(requestCacheIdMapFile));
            final List<PutObject> puts = cacheRequest.getPuts();

            final List<CacheObject> responseCacheObjects = new ArrayList<>();
            for (PutObject putItem : puts) {
                final String id = putItem.getType().equals("json")
                        ? putItem.getValue().get("id").textValue() + "@" + resolvePriceForJsonMediaType(putItem)
                        : putItem.getValue().textValue();

                final String uuid = jsonNodeMatcher.get(id).textValue();
                responseCacheObjects.add(CacheObject.of(uuid));
            }
            return mapper.writeValueAsString(BidCacheResponse.of(responseCacheObjects));
        } catch (IOException e) {
            throw new IOException("Error while matching cache ids");
        }
    }

    private static String resolvePriceForJsonMediaType(PutObject putItem) {
        final JsonNode extObject = putItem.getValue().get("ext");
        final JsonNode origBidCpm = extObject != null ? extObject.get("origbidcpm") : null;
        return origBidCpm != null ? origBidCpm.toString() : putItem.getValue().get("price").toString();
    }

    /**
     * Cache debug fields "requestbody" and "responsebody" are escaped JSON strings.
     * This customization allows to compare them with actual values as usual JSON objects.
     */
    static Customization openrtbCacheDebugCustomization() {
        final ValueMatcher<Object> jsonStringValueMatcher = (actual, expected) -> {
            try {
                return !JSONCompare.compareJSON(actual.toString(), expected.toString(), JSONCompareMode.NON_EXTENSIBLE)
                        .failed();
            } catch (JSONException e) {
                throw new RuntimeException("Unexpected json exception", e);
            }
        };

        final ArrayValueMatcher<Object> arrayValueMatcher = new ArrayValueMatcher<>(new CustomComparator(
                JSONCompareMode.NON_EXTENSIBLE,
                new Customization("ext.debug.httpcalls.cache[*].requestbody", jsonStringValueMatcher),
                new Customization("ext.debug.httpcalls.cache[*].responsebody", jsonStringValueMatcher)));

        return new Customization("ext.debug.httpcalls.cache", arrayValueMatcher);
    }

    static Customization headersDebugCustomization() {
        return new Customization("**.requestheaders.x-prebid", (o1, o2) -> true);
    }

    protected static void assertJsonEquals(String file,
                                           Response response,
                                           List<String> bidders,
                                           Customization... customizations) throws IOException, JSONException {
        final List<Customization> fullCustomizations = new ArrayList<>(Arrays.asList(customizations));
        fullCustomizations.add(new Customization("ext.prebid.auctiontimestamp", (o1, o2) -> true));
        fullCustomizations.add(new Customization("ext.responsetimemillis.cache", (o1, o2) -> true));
        String expectedRequest = replaceStaticInfo(jsonFrom(file));
        for (String bidder : bidders) {
            expectedRequest = replaceBidderRelatedStaticInfo(expectedRequest, bidder);
            fullCustomizations.add(new Customization(
                    String.format("ext.responsetimemillis.%s", bidder), (o1, o2) -> true));
        }

        JSONAssert.assertEquals(expectedRequest, response.asString(),
                new CustomComparator(JSONCompareMode.NON_EXTENSIBLE,
                        fullCustomizations.toArray(new Customization[0])));
    }

    private static String replaceStaticInfo(String json) {

        return json.replaceAll("\\{\\{ cache.endpoint }}", CACHE_ENDPOINT)
                .replaceAll("\\{\\{ cache.resource_url }}", CACHE_ENDPOINT + "?uuid=")
                .replaceAll("\\{\\{ cache.host }}", HOST_AND_PORT)
                .replaceAll("\\{\\{ cache.path }}", CACHE_PATH)
                .replaceAll("\\{\\{ event.url }}", "http://localhost:8080/event?");
    }

    private static String replaceBidderRelatedStaticInfo(String json, String bidder) {

        return json.replaceAll("\\{\\{ " + bidder + "\\.exchange_uri }}",
                "http://" + HOST_AND_PORT + "/" + bidder + "-exchange");
    }

    static BidCacheRequestPattern equalToBidCacheRequest(String json) {
        return new BidCacheRequestPattern(json);
    }

    protected static StringValuePattern notEmpty() {
        return matching(ANY_SYMBOL_REGEX);
    }

    public static class CacheResponseTransformer extends ResponseTransformer {

        @Override
        public com.github.tomakehurst.wiremock.http.Response transform(
                Request request, com.github.tomakehurst.wiremock.http.Response response, FileSource files,
                Parameters parameters) {

            final String newResponse;
            try {
                newResponse = cacheResponseFromRequestJson(
                        request.getBodyAsString(),
                        parameters.getString("matcherName"));
            } catch (IOException e) {
                return com.github.tomakehurst.wiremock.http.Response.response()
                        .body(e.getMessage())
                        .status(500)
                        .build();
            }
            return com.github.tomakehurst.wiremock.http.Response.response().body(newResponse).status(200).build();
        }

        @Override
        public String getName() {
            return "cache-response-transformer";
        }

        @Override
        public boolean applyGlobally() {
            return false;
        }
    }
}
