package org.prebid.server.handler.info;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import lombok.Value;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.BidderInfo;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.model.Endpoint;
import org.prebid.server.util.HttpUtil;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BidderDetailsHandler implements Handler<RoutingContext> {

    private static final String BIDDER_NAME_PARAM = "bidderName";
    private static final String ALL_PARAM_VALUE = "all";

    private final JacksonMapper mapper;
    private final Map<String, String> bidderInfos;

    public BidderDetailsHandler(BidderCatalog bidderCatalog, JacksonMapper mapper) {
        validateAliases(Objects.requireNonNull(bidderCatalog));
        this.mapper = Objects.requireNonNull(mapper);
        this.bidderInfos = createBidderInfos(bidderCatalog);
    }

    private static void validateAliases(BidderCatalog bidderCatalog) {
        if (bidderCatalog.names().contains(ALL_PARAM_VALUE)) {
            throw new IllegalArgumentException(
                    String.format("There is '%s' bidder or alias configured which is unacceptable.", ALL_PARAM_VALUE));
        }
    }

    private Map<String, String> createBidderInfos(BidderCatalog bidderCatalog) {
        final Map<String, ObjectNode> nameToInfo = bidderCatalog.names().stream()
                .collect(Collectors.toMap(Function.identity(), name -> bidderNode(bidderCatalog, name)));

        final Map<String, ObjectNode> allToInfos = Collections.singletonMap(ALL_PARAM_VALUE, allInfos(nameToInfo));

        return Stream.of(nameToInfo, allToInfos)
                .flatMap(map -> map.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, map -> mapper.encodeToString(map.getValue())));
    }

    private ObjectNode bidderNode(BidderCatalog bidderCatalog, String name) {
        final BidderInfo bidderInfo = bidderCatalog.bidderInfoByName(name);
        return mapper.mapper().valueToTree(BidderInfoResponseModel.from(bidderInfo));
    }

    private ObjectNode allInfos(Map<String, ObjectNode> nameToInfo) {
        return mapper.mapper().valueToTree(new TreeMap<>(nameToInfo));
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final String bidderName = routingContext.request().getParam(BIDDER_NAME_PARAM);
        final String endpoint = String.format("%s/%s", Endpoint.info_bidders.value(), bidderName);

        if (bidderInfos.containsKey(bidderName)) {
            HttpUtil.executeSafely(routingContext, endpoint,
                    response -> response
                            .putHeader(HttpUtil.CONTENT_TYPE_HEADER, HttpHeaderValues.APPLICATION_JSON)
                            .end(bidderInfos.get(bidderName)));
        } else {
            HttpUtil.executeSafely(routingContext, endpoint,
                    response -> response
                            .setStatusCode(HttpResponseStatus.NOT_FOUND.code())
                            .end());
        }
    }

    @Value(staticConstructor = "of")
    private static class BidderInfoResponseModel {

        private static final String STATUS_ACTIVE = "ACTIVE";
        private static final String STATUS_DISABLED = "DISABLED";

        String status;

        @JsonProperty("usesHttps")
        boolean usesHttps;

        BidderInfo.MaintainerInfo maintainer;

        BidderInfo.CapabilitiesInfo capabilities;

        @JsonProperty("aliasOf")
        String aliasOf;

        private static BidderInfoResponseModel from(BidderInfo bidderInfo) {
            return of(
                    bidderInfo.isEnabled() ? STATUS_ACTIVE : STATUS_DISABLED,
                    bidderInfo.isUsesHttps(),
                    bidderInfo.getMaintainer(),
                    bidderInfo.getCapabilities(),
                    bidderInfo.getAliasOf());
        }
    }
}
