package org.prebid.server.hooks.modules.optable.targeting.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.gpp.encoder.GppModel;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Data;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Segment;
import com.iab.openrtb.request.Uid;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.prebid.server.activity.infrastructure.ActivityInfrastructure;
import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.TimeoutContext;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.hooks.modules.optable.targeting.model.EnrichmentStatus;
import org.prebid.server.hooks.modules.optable.targeting.model.ModuleContext;
import org.prebid.server.hooks.modules.optable.targeting.model.Query;
import org.prebid.server.hooks.modules.optable.targeting.model.config.CacheProperties;
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.Audience;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.AudienceId;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.Ortb2;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.json.ObjectMapperProvider;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.privacy.model.PrivacyContext;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

public abstract class BaseOptableTest {

    protected final ObjectMapper mapper = ObjectMapperProvider.mapper();

    protected final JsonMerger jsonMerger = new JsonMerger(new JacksonMapper(mapper));

    protected ModuleContext givenModuleContext() {
        return givenModuleContext(null);
    }

    protected ModuleContext givenModuleContext(List<Audience> audiences) {
        final ModuleContext moduleContext = new ModuleContext();
        moduleContext.setTargeting(audiences);
        moduleContext.setEnrichRequestStatus(EnrichmentStatus.success());

        return moduleContext;
    }

    protected AuctionContext givenAuctionContext(ActivityInfrastructure activityInfrastructure, Timeout timeout) {
        final GppModel gppModel = new GppModel();
        final TcfContext tcfContext = TcfContext.builder().build();
        final GppContext gppContext = new GppContext(
                GppContext.Scope.of(gppModel, Set.of(1)),
                GppContext.Regions.builder().build());

        return AuctionContext.builder()
                .bidRequest(givenBidRequest())
                .activityInfrastructure(activityInfrastructure)
                .privacyContext(PrivacyContext.of(Privacy.builder().build(), tcfContext, "8.8.8.8"))
                .gppContext(gppContext)
                .timeoutContext(TimeoutContext.of(0, timeout, 1))
                .build();
    }

    protected BidRequest givenBidRequest() {
        return givenBidRequestWithUserEids(null);
    }

    protected static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer) {
        return bidRequestCustomizer.apply(BidRequest.builder().id("requestId")).build();
    }

    protected BidRequest givenBidRequestWithUserEids(List<Eid> eids) {
        return BidRequest.builder()
                .user(givenUser(eids))
                .device(givenDevice())
                .cur(List.of("USD"))
                .build();
    }

    protected BidRequest givenBidRequestWithUserData(List<Data> data) {
        return BidRequest.builder()
                .user(givenUserWithData(data))
                .device(givenDevice())
                .cur(List.of("USD"))
                .build();
    }

    protected BidResponse givenBidResponse() {
        final ObjectNode targetingNode = mapper.createObjectNode();
        targetingNode.set("attribute1", TextNode.valueOf("value1"));
        targetingNode.set("attribute2", TextNode.valueOf("value1"));
        final ObjectNode bidderNode = mapper.createObjectNode();
        bidderNode.set("targeting", targetingNode);
        final ObjectNode bidExtNode = mapper.createObjectNode();
        bidExtNode.set("prebid", bidderNode);

        return BidResponse.builder()
                .seatbid(List.of(SeatBid.builder()
                        .bid(List.of(Bid.builder().ext(bidExtNode).build()))
                        .build()))
                .build();
    }

    protected TargetingResult givenTargetingResultWithEids(List<Eid> eids) {
        return givenTargetingResult(eids, null);
    }

    protected TargetingResult givenTargetingResultWithData(List<Data> data) {
        return givenTargetingResult(null, data);
    }

    protected TargetingResult givenTargetingResult() {
        return givenTargetingResult(
                List.of(Eid.builder()
                        .source("source")
                        .uids(List.of(Uid.builder().id("id").build()))
                        .build()),
                List.of(Data.builder()
                        .id("id")
                        .segment(List.of(Segment.builder().id("id").build()))
                        .build()));
    }

    protected TargetingResult givenTargetingResult(List<Eid> eids, List<Data> data) {
        return new TargetingResult(
                List.of(new Audience(
                        "provider",
                        List.of(new AudienceId("id")),
                        "keyspace",
                        1)),
                new Ortb2(new org.prebid.server.hooks.modules.optable.targeting.model.openrtb.User(eids, data)));
    }

    protected TargetingResult givenEmptyTargetingResult() {
        return new TargetingResult(Collections.emptyList(), new Ortb2(null));
    }

    protected User givenUser() {
        return givenUser(null);
    }

    protected User givenUser(List<Eid> eids) {
        final ObjectNode optable = mapper.createObjectNode();
        optable.set("email", TextNode.valueOf("email"));
        optable.set("phone", TextNode.valueOf("phone"));
        optable.set("zip", TextNode.valueOf("zip"));
        optable.set("vid", TextNode.valueOf("vid"));

        final ExtUser extUser = ExtUser.builder().build();
        extUser.addProperty("optable", optable);

        return User.builder()
                .eids(eids)
                .geo(Geo.builder().country("country-u").region("region-u").build())
                .ext(extUser)
                .build();
    }

    protected User givenUserWithData(List<Data> data) {
        return User.builder()
                .data(data)
                .build();
    }

    protected Device givenDevice() {
        return Device.builder().geo(Geo.builder().country("country-d").region("region-d").build()).build();
    }

    protected HttpClientResponse givenSuccessHttpResponse(String fileName) {
        final MultiMap headers = HeadersMultiMap.headers().add("Content-Type", "application/json");
        return HttpClientResponse.of(HttpStatus.SC_OK, headers, givenBodyFromFile(fileName));
    }

    protected HttpClientResponse givenFailHttpResponse(String fileName) {
        return givenFailHttpResponse(HttpStatus.SC_BAD_REQUEST, fileName);
    }

    protected HttpClientResponse givenFailHttpResponse(int statusCode, String fileName) {
        return HttpClientResponse.of(statusCode, null, givenBodyFromFile(fileName));
    }

    protected String givenBodyFromFile(String fileName) {
        InputStream inputStream = null;
        try {
            inputStream = Files.newInputStream(Paths.get("src/test/resources/" + fileName));
            return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    protected OptableTargetingProperties givenOptableTargetingProperties(boolean enableCache) {
        return givenOptableTargetingProperties("key", enableCache);
    }

    protected OptableTargetingProperties givenOptableTargetingProperties(String key, boolean enableCache) {
        final CacheProperties cacheProperties = new CacheProperties();
        cacheProperties.setEnabled(enableCache);

        final OptableTargetingProperties optableTargetingProperties = new OptableTargetingProperties();
        optableTargetingProperties.setApiEndpoint("endpoint");
        optableTargetingProperties.setTenant("accountId");
        optableTargetingProperties.setOrigin("origin");
        optableTargetingProperties.setApiKey(key);
        optableTargetingProperties.setPpidMapping(Map.of("c", "id"));
        optableTargetingProperties.setAdserverTargeting(true);
        optableTargetingProperties.setTimeout(100L);
        optableTargetingProperties.setCache(cacheProperties);

        return optableTargetingProperties;
    }

    protected Query givenQuery() {
        return Query.of("?que", "ry");
    }
}
