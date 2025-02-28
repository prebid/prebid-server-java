package org.prebid.server.hooks.modules.optable.targeting.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Uid;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.TimeoutContext;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.hooks.modules.optable.targeting.model.EnrichmentStatus;
import org.prebid.server.hooks.modules.optable.targeting.model.Metrics;
import org.prebid.server.hooks.modules.optable.targeting.model.ModuleContext;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.Audience;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.AudienceId;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.Data;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.Ortb2;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.Segment;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

public abstract class BaseOptableTest {

    private final ObjectMapper mapper = new ObjectMapper();

    protected ModuleContext givenModuleContext() {
        return givenModuleContext(null);
    }

    protected ModuleContext givenModuleContext(List<Audience> audiences) {
        final ModuleContext moduleContext = new ModuleContext();
        moduleContext.setMetrics(Metrics.builder()
                .moduleStartTime(System.currentTimeMillis())
                .build());
        moduleContext.setTargeting(audiences);
        moduleContext.setEnrichRequestStatus(EnrichmentStatus.success());

        return moduleContext;
    }

    protected AuctionContext givenAuctionContext(Timeout timeout) {
        return AuctionContext.builder()
                .bidRequest(givenBidRequest())
                .timeoutContext(TimeoutContext.of(0, timeout, 1))
                .build();
    }

    protected BidRequest givenBidRequest() {
        return BidRequest.builder()
                .user(givenUser())
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
                                .bid(List.of(Bid.builder()
                                                .ext(bidExtNode)
                                        .build()))
                        .build()))
                .build();
    }

    protected TargetingResult givenTargetingResult() {
        return new TargetingResult(
                List.of(new Audience(
                        "provider",
                        List.of(new AudienceId("id")),
                        "keyspace",
                        1
                )),
                new Ortb2(
                        new org.prebid.server.hooks.modules.optable.targeting.model.openrtb.User(
                                List.of(Eid.builder()
                                                .source("source")
                                                .uids(List.of(Uid.builder()
                                                                .id("id")
                                                        .build()))
                                        .build()),
                                List.of(new Data("id", List.of(new Segment("id", null))))
                        )
                )
        );
    }

    protected TargetingResult givenEmptyTargetingResult() {
        return new TargetingResult(Collections.emptyList(), new Ortb2(null));
    }

    protected User givenUser() {
        final ObjectNode optable = mapper.createObjectNode();
        optable.set("email", TextNode.valueOf("email"));
        optable.set("phone", TextNode.valueOf("phone"));
        optable.set("zip", TextNode.valueOf("zip"));
        optable.set("vid", TextNode.valueOf("vid"));

        final ExtUser extUser = ExtUser.builder().build();
        extUser.addProperty("optable", optable);

        return User.builder()
                .geo(Geo.builder().country("country-u").region("region-u").build())
                .ext(extUser)
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
}
