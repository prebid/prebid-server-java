package org.prebid.server.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.internal.mapping.Jackson2Mapper;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.prebid.server.VertxTest;
import org.prebid.server.cache.proto.request.BidCacheRequest;
import org.prebid.server.cache.proto.request.PutObject;
import org.prebid.server.cache.proto.response.BidCacheResponse;
import org.prebid.server.cache.proto.response.CacheObject;
import org.prebid.server.it.hooks.TestHooksConfiguration;
import org.prebid.server.it.util.BidCacheRequestPattern;
import org.prebid.server.model.Endpoint;
import org.prebid.server.util.IntegrationTestsUtil;
import org.prebid.server.version.PrebidVersionProvider;
import org.skyscreamer.jsonassert.ArrayValueMatcher;
import org.skyscreamer.jsonassert.Customization;
import org.skyscreamer.jsonassert.JSONCompare;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.ValueMatcher;
import org.skyscreamer.jsonassert.comparator.CustomComparator;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.prebid.server.util.IntegrationTestsUtil.replaceBidderRelatedStaticInfo;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource({"test-application.properties", "test-application-hooks.properties"})
@Import(TestHooksConfiguration.class)
public abstract class IntegrationTest extends VertxTest {

    private static final int APP_PORT = 8080;
    private static final int WIREMOCK_PORT = 8090;
    private static final Pattern UTC_MILLIS_PATTERN =
            Pattern.compile(".*([0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}Z).*");

    @SuppressWarnings("unchecked")
    @ClassRule
    public static final WireMockClassRule WIRE_MOCK_RULE = new WireMockClassRule(options()
            .port(WIREMOCK_PORT)
            .gzipDisabled(true)
            .jettyStopTimeout(5000L)
            .extensions(IntegrationTest.CacheResponseTransformer.class));

    protected static final RequestSpecification SPEC = spec(APP_PORT);
    private static final String HOST_AND_PORT = "localhost:" + WIREMOCK_PORT;
    private static final String CACHE_PATH = "/cache";
    private static final String CACHE_ENDPOINT = "http://" + HOST_AND_PORT + CACHE_PATH;
    private static final String USER_SERVICE_PATH = "/user-data-details";
    private static final String USER_SERVICE_ENDPOINT = "http://" + HOST_AND_PORT + USER_SERVICE_PATH;

    @BeforeClass
    public static void setUp() throws IOException {
        WIRE_MOCK_RULE.stubFor(get(urlPathEqualTo("/periodic-update"))
                .willReturn(aResponse().withBody(jsonFrom("storedrequests/test-periodic-refresh.json"))));
        WIRE_MOCK_RULE.stubFor(get(urlPathEqualTo("/currency-rates"))
                .willReturn(aResponse().withBody(jsonFrom("currency/latest.json"))));
    }

    @After
    public void resetWireMock() {
        WIRE_MOCK_RULE.resetAll();
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
        return IntegrationTestsUtil.responseFor(file, endpoint, SPEC);
    }

    protected static String jsonFrom(String file) throws IOException {
        // workaround to clear formatting
        return mapper.writeValueAsString(mapper.readTree(IntegrationTest.class.getResourceAsStream(file)));
    }

    protected static String jsonFrom(String file, PrebidVersionProvider prebidVersionProvider) throws IOException {
        // workaround to clear formatting
        return mapper.writeValueAsString(mapper.readTree(IntegrationTest.class.getResourceAsStream(file)))
                .replace("{{ pbs.java.version }}", prebidVersionProvider.getNameVersionRecord());
    }

    protected static String openrtbAuctionResponseFrom(String templatePath, Response response, List<String> bidders)
            throws IOException {

        return auctionResponseFrom(templatePath, response, "ext.responsetimemillis.%s",
                "ext.debug.httpcalls.userservice[0].requestbody", bidders);
    }

    private static String auctionResponseFrom(String templatePath, Response response, String responseTimePath,
                                              String responseUserTimePath, List<String> bidders) throws IOException {

        String result = replaceStaticInfo(jsonFrom(templatePath));

        for (final String bidder : bidders) {
            result = result.replaceAll("\\{\\{ " + bidder + "\\.exchange_uri }}",
                    "http://" + HOST_AND_PORT + "/" + bidder + "-exchange");
            result = setResponseTime(response, result, bidder, responseTimePath);
        }

        if (StringUtils.isNotBlank(responseUserTimePath)) {
            result = setUserServiceTimestamp(response, result, responseUserTimePath);
        }
        return result;
    }

    private static String setUserServiceTimestamp(Response response, String expectedResponseJson,
                                                  String responseUserTimePath) {
        final Object val;
        try {
            val = response.path(responseUserTimePath);
        } catch (Exception e) {
            return expectedResponseJson;
        }

        if (val == null) {
            return expectedResponseJson;
        }
        final String userRequest = val.toString();
        final Matcher m = UTC_MILLIS_PATTERN.matcher(userRequest);
        final String userRequestDate;
        if (m.find()) {
            userRequestDate = m.group(1);
            return expectedResponseJson.replaceAll("\\{\\{ userservice_time }}", userRequestDate);
        }
        return expectedResponseJson;
    }

    private static String setResponseTime(Response response,
                                          String expectedResponseJson,
                                          String bidder,
                                          String responseTimePath) {

        final Object val = response.path(responseTimePath.formatted(bidder));
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

        IntegrationTestsUtil.assertJsonEquals(
                file,
                response,
                bidders,
                (json, bidder) -> replaceBidderRelatedStaticInfo(json, bidder, WIREMOCK_PORT),
                IntegrationTest::replaceStaticInfo,
                customizations);
    }

    private static String replaceStaticInfo(String json) {
        return json.replaceAll("\\{\\{ cache.endpoint }}", CACHE_ENDPOINT)
                .replaceAll("\\{\\{ cache.resource_url }}", CACHE_ENDPOINT + "?uuid=")
                .replaceAll("\\{\\{ cache.host }}", HOST_AND_PORT)
                .replaceAll("\\{\\{ cache.path }}", CACHE_PATH)
                .replaceAll("\\{\\{ userservice_uri }}", USER_SERVICE_ENDPOINT)
                .replaceAll("\\{\\{ event.url }}", "http://localhost:8080/event?");
    }

    static BidCacheRequestPattern equalToBidCacheRequest(String json) {
        return new BidCacheRequestPattern(json);
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

    static final String LINE_ITEM_RESPONSE_ORDER = "lineItemResponseOrder";
    static final String ID_TO_EXECUTION_PARAMETERS = "idToExecutionParameters";

    public static class ResponseOrderTransformer extends ResponseTransformer {

        private static final String LINE_ITEM_PATH = "/imp/0/ext/rp/target/line_item";

        private final Lock lock = new ReentrantLock();
        private final Condition lockCondition = lock.newCondition();

        @Override
        @SuppressWarnings("unchecked")
        public com.github.tomakehurst.wiremock.http.Response transform(
                Request request,
                com.github.tomakehurst.wiremock.http.Response response,
                FileSource files,
                Parameters parameters) {

            final Queue<String> lineItemResponseOrder =
                    (Queue<String>) parameters.get(LINE_ITEM_RESPONSE_ORDER);
            final Map<String, BidRequestExecutionParameters> idToParameters =
                    (Map<String, BidRequestExecutionParameters>) parameters.get(ID_TO_EXECUTION_PARAMETERS);

            final String requestDealId;
            try {
                requestDealId = readStringValue(mapper.readTree(request.getBodyAsString()), LINE_ITEM_PATH);
            } catch (IOException e) {
                throw new RuntimeException("Request should contain imp/ext/rp/target/line_item for deals request");
            }

            final BidRequestExecutionParameters requestParameters = idToParameters.get(requestDealId);

            waitForTurn(lineItemResponseOrder, requestParameters.getDealId(), requestParameters.getDelay());

            return com.github.tomakehurst.wiremock.http.Response.response()
                    .body(requestParameters.getBody())
                    .status(requestParameters.getStatus())
                    .build();
        }

        private void waitForTurn(Queue<String> dealsResponseOrder, String id, Long delay) {
            lock.lock();
            try {
                while (!Objects.equals(dealsResponseOrder.peek(), id)) {
                    lockCondition.await();
                }
                TimeUnit.MILLISECONDS.sleep(delay);
                dealsResponseOrder.poll();
                lockCondition.signalAll();
            } catch (InterruptedException e) {
                throw new RuntimeException("Failed on waiting to return bid request for lineItem id = " + id);
            } finally {
                lock.unlock();
            }
        }

        private String readStringValue(JsonNode jsonNode, String path) {
            return jsonNode.at(path).asText();
        }

        @Override
        public String getName() {
            return "response-order-transformer";
        }

        @Override
        public boolean applyGlobally() {
            return false;
        }
    }

    @Value
    @AllArgsConstructor(staticName = "of")
    static class BidRequestExecutionParameters {
        String dealId;

        String body;

        Integer status;

        Long delay;
    }
}
