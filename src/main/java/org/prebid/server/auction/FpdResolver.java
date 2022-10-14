package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.proto.openrtb.ext.request.ExtBidderConfig;
import org.prebid.server.proto.openrtb.ext.request.ExtBidderConfigOrtb;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidBidderConfig;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidData;
import org.prebid.server.proto.request.Targeting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class FpdResolver {

    private static final String USER = "user";
    private static final String SITE = "site";
    private static final String BIDDERS = "bidders";
    private static final String APP = "app";
    private static final Set<String> KNOWN_FPD_ATTRIBUTES = Set.of(USER, SITE, APP, BIDDERS);
    private static final String ALLOW_ALL_BIDDERS = "*";
    private static final String CONTEXT = "context";
    private static final String DATA = "data";

    private final JacksonMapper jacksonMapper;
    private final JsonMerger jsonMerger;

    public FpdResolver(JacksonMapper jacksonMapper, JsonMerger jsonMerger) {
        this.jacksonMapper = Objects.requireNonNull(jacksonMapper);
        this.jsonMerger = Objects.requireNonNull(jsonMerger);
    }

    public User resolveUser(User originUser, ObjectNode fpdUser) {
        return mergeFpd(originUser, fpdUser, User.class);
    }

    private <T> T mergeFpd(T original, ObjectNode fpd, Class<T> tClass) {
        if (fpd == null || fpd.isNull() || fpd.isMissingNode()) {
            return original;
        }

        final ObjectMapper mapper = jacksonMapper.mapper();

        final JsonNode originalAsJsonNode = original != null
                ? mapper.valueToTree(original)
                : NullNode.getInstance();
        final JsonNode merged = jsonMerger.merge(fpd, originalAsJsonNode);
        try {
            return mapper.treeToValue(merged, tClass);
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException("Can't convert merging result class " + tClass.getName());
        }
    }

    public App resolveApp(App originApp, ObjectNode fpdApp) {
        return mergeFpd(originApp, fpdApp, App.class);
    }

    public Site resolveSite(Site originSite, ObjectNode fpdSite) {
        return mergeFpd(originSite, fpdSite, Site.class);
    }

    public ObjectNode resolveImpExt(ObjectNode impExt, ObjectNode targeting) {
        if (targeting == null) {
            return impExt;
        }

        KNOWN_FPD_ATTRIBUTES.forEach(targeting::remove);
        if (!targeting.fieldNames().hasNext()) {
            return impExt;
        }

        if (impExt == null) {
            return jacksonMapper.mapper().createObjectNode()
                    .set(DATA, targeting);
        }

        final JsonNode extImpData = impExt.get(DATA);

        final ObjectNode resolvedData = extImpData != null
                ? (ObjectNode) jsonMerger.merge(targeting, extImpData)
                : targeting;

        return impExt.set(DATA, resolvedData);
    }

    /**
     * @param impExt might be modified within method
     */
    public ObjectNode resolveImpExt(ObjectNode impExt, boolean useFirstPartyData) {
        removeOrReplace(impExt, CONTEXT, sanitizeImpExtContext(impExt, useFirstPartyData));
        removeOrReplace(impExt, DATA, sanitizeImpExtData(impExt, useFirstPartyData));

        return impExt;
    }

    private JsonNode sanitizeImpExtContext(ObjectNode originalImpExt, boolean useFirstPartyData) {
        if (!originalImpExt.hasNonNull(CONTEXT)) {
            return null;
        }

        final JsonNode updatedContextNode = originalImpExt.get(CONTEXT).deepCopy();
        if (!useFirstPartyData && updatedContextNode.hasNonNull(DATA)) {
            ((ObjectNode) updatedContextNode).remove(DATA);
        }

        return updatedContextNode.isObject() && updatedContextNode.isEmpty() ? null : updatedContextNode;
    }

    private JsonNode sanitizeImpExtData(ObjectNode impExt, boolean useFirstPartyData) {
        if (!useFirstPartyData) {
            return null;
        }

        final JsonNode contextNode = impExt.hasNonNull(CONTEXT) ? impExt.get(CONTEXT) : null;
        final JsonNode contextDataNode =
                contextNode != null && contextNode.hasNonNull(DATA) ? contextNode.get(DATA) : null;

        final JsonNode dataNode = impExt.get(DATA);

        final boolean dataIsNullOrObject =
                dataNode == null || dataNode.isObject();
        final boolean contextDataIsObject =
                contextDataNode != null && !contextDataNode.isNull() && contextDataNode.isObject();

        final JsonNode mergedDataNode = dataIsNullOrObject && contextDataIsObject
                ? dataNode != null ? jsonMerger.merge(contextDataNode, dataNode) : contextDataNode
                : dataNode;

        if (mergedDataNode != null && !mergedDataNode.isNull()) {
            return mergedDataNode;
        }

        return null;
    }

    private void removeOrReplace(ObjectNode impExt, String field, JsonNode jsonNode) {
        if (jsonNode == null) {
            impExt.remove(field);
        } else {
            impExt.set(field, jsonNode);
        }
    }

    public ExtRequest resolveBidRequestExt(ExtRequest extRequest, Targeting targeting) {
        if (targeting == null) {
            return extRequest;
        }

        final ExtRequestPrebid extRequestPrebid = extRequest != null ? extRequest.getPrebid() : null;
        final ExtRequestPrebidData extRequestPrebidData = extRequestPrebid != null
                ? extRequestPrebid.getData()
                : null;

        final ExtRequestPrebidData resolvedExtRequestPrebidData = resolveExtRequestPrebidData(extRequestPrebidData,
                targeting.getBidders());
        final List<ExtRequestPrebidBidderConfig> resolvedBidderConfig = createAllowedAllBidderConfig(targeting);

        if (resolvedExtRequestPrebidData != null || resolvedBidderConfig != null) {
            final ExtRequestPrebid.ExtRequestPrebidBuilder prebidBuilder = extRequestPrebid != null
                    ? extRequestPrebid.toBuilder()
                    : ExtRequestPrebid.builder();
            return ExtRequest.of(prebidBuilder
                    .data(resolvedExtRequestPrebidData != null
                            ? resolvedExtRequestPrebidData
                            : extRequestPrebidData)
                    .bidderconfig(resolvedBidderConfig)
                    .build());
        }

        return extRequest;
    }

    private ExtRequestPrebidData resolveExtRequestPrebidData(ExtRequestPrebidData data, List<String> fpdBidders) {
        if (CollectionUtils.isEmpty(fpdBidders)) {
            return null;
        }
        final List<String> originBidders = data != null ? data.getBidders() : Collections.emptyList();
        return CollectionUtils.isEmpty(originBidders)
                ? ExtRequestPrebidData.of(fpdBidders, null)
                : ExtRequestPrebidData.of(mergeBidders(fpdBidders, originBidders), null);
    }

    private List<String> mergeBidders(List<String> fpdBidders, List<String> originBidders) {
        final HashSet<String> resolvedBidders = new HashSet<>(originBidders);
        resolvedBidders.addAll(fpdBidders);
        return new ArrayList<>(resolvedBidders);
    }

    private List<ExtRequestPrebidBidderConfig> createAllowedAllBidderConfig(Targeting targeting) {
        final ObjectNode userNode = targeting.getUser();
        final ObjectNode siteNode = targeting.getSite();
        if (userNode == null && siteNode == null) {
            return null;
        }
        final List<String> bidders = Collections.singletonList(ALLOW_ALL_BIDDERS);

        return Collections.singletonList(ExtRequestPrebidBidderConfig.of(bidders,
                ExtBidderConfig.of(null, ExtBidderConfigOrtb.of(siteNode, null, userNode))));
    }
}
