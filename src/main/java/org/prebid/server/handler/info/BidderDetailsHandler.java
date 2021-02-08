package org.prebid.server.handler.info;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import lombok.Value;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.settings.bidder.BidderInfo;
import org.prebid.server.settings.bidder.CapabilitiesInfo;
import org.prebid.server.settings.bidder.MaintainerInfo;
import org.prebid.server.settings.bidder.MediaTypeMappings;
import org.prebid.server.settings.bidder.PlatformInfo;
import org.prebid.server.util.HttpUtil;

import java.util.Collections;
import java.util.List;
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
        bidderInfos = createBidderInfos(bidderCatalog);
    }

    private static void validateAliases(BidderCatalog bidderCatalog) {
        if (bidderCatalog.aliases().contains(ALL_PARAM_VALUE)) {
            throw new IllegalArgumentException(
                    String.format("The '%s' bidder has '%s' alias configured which is unacceptable.",
                            bidderCatalog.nameByAlias(ALL_PARAM_VALUE), ALL_PARAM_VALUE));
        }
    }

    /**
     * Returns a {@link Map} with bidder name (or alias, or "all" keyword) as a key
     * and json-encoded string of {@link BidderInfoResponseModel} as a value.
     */
    private Map<String, String> createBidderInfos(BidderCatalog bidderCatalog) {
        final Map<String, ObjectNode> nameToInfo = bidderCatalog.names().stream()
                .filter(bidderCatalog::isActive)
                .collect(Collectors.toMap(Function.identity(), name -> bidderNode(bidderCatalog, name)));

        final Map<String, ObjectNode> aliasToInfo = bidderCatalog.aliases().stream()
                .filter(alias -> bidderCatalog.isActive(bidderCatalog.nameByAlias(alias)))
                .collect(Collectors.toMap(Function.identity(), alias -> aliasNode(bidderCatalog, alias)));

        final Map<String, ObjectNode> allToInfos = Collections.singletonMap(
                ALL_PARAM_VALUE, allInfos(nameToInfo, aliasToInfo));

        return Stream.of(nameToInfo, aliasToInfo, allToInfos)
                .flatMap(map -> map.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, map -> mapper.encode(map.getValue())));
    }

    /**
     * Returns bidder info as {@link ObjectNode}.
     */
    private ObjectNode bidderNode(BidderCatalog bidderCatalog, String name) {
        final BidderInfo bidderInfo = bidderCatalog.bidderInfoByName(name);
        return mapper.mapper().valueToTree(BidderInfoResponseModel.from(bidderInfo));
    }

    /**
     * Returns alias info as {@link ObjectNode}.
     */
    private ObjectNode aliasNode(BidderCatalog bidderCatalog, String alias) {
        final String name = bidderCatalog.nameByAlias(alias);

        final ObjectNode node = bidderNode(bidderCatalog, name);
        node.set("aliasOf", new TextNode(name));
        return node;
    }

    /**
     * Returns a {@link Map} of all bidder's infos sorted by name (and alias) as {@link ObjectNode}.
     */
    private ObjectNode allInfos(Map<String, ObjectNode> nameToInfo, Map<String, ObjectNode> aliasToInfo) {
        final Map<String, ObjectNode> result = new TreeMap<>();
        result.putAll(nameToInfo);
        result.putAll(aliasToInfo);
        return mapper.mapper().valueToTree(result);
    }

    @Override
    public void handle(RoutingContext context) {
        final String bidderName = context.request().getParam(BIDDER_NAME_PARAM);

        if (bidderInfos.containsKey(bidderName)) {
            context.response()
                    .putHeader(HttpUtil.CONTENT_TYPE_HEADER, HttpHeaderValues.APPLICATION_JSON)
                    .end(bidderInfos.get(bidderName));
        } else {
            context.response()
                    .setStatusCode(HttpResponseStatus.NOT_FOUND.code())
                    .end();
        }
    }

    @Value
    private static class BidderInfoResponseModel {

        MaintainerInfo maintainer;

        Capabilities capabilities;

        static BidderInfoResponseModel from(BidderInfo bidderInfo) {
            final CapabilitiesInfo capabilities = bidderInfo.getCapabilities();
            return new BidderInfoResponseModel(bidderInfo.getMaintainer(), Capabilities.from(capabilities));
        }
    }

    @Value(staticConstructor = "of")
    private static class Capabilities {

        SupportedMediaTypes app;

        SupportedMediaTypes site;

        static Capabilities from(CapabilitiesInfo capabilitiesInfo) {
            final PlatformInfo platformAppInfo = capabilitiesInfo.getApp();
            final PlatformInfo platformSiteInfo = capabilitiesInfo.getSite();
            return Capabilities.of(
                    SupportedMediaTypes.from(platformAppInfo),
                    SupportedMediaTypes.from(platformSiteInfo));
        }
    }

    @Value(staticConstructor = "of")
    public static class SupportedMediaTypes {

        @JsonProperty("mediaTypes")
        List<String> mediaTypes;

        static SupportedMediaTypes from(PlatformInfo platformInfo) {
            final List<String> supportedMedia = platformInfo.getSupportedMediaTypes().getMediaTypeMappings().stream()
                    .map(MediaTypeMappings.MediaTypeMapping::getMediaType)
                    .collect(Collectors.toList());

            return SupportedMediaTypes.of(supportedMedia);
        }
    }

}
