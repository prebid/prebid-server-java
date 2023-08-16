package org.prebid.server.auction;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Data;
import com.iab.openrtb.request.Dooh;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtBidderConfig;
import org.prebid.server.proto.openrtb.ext.request.ExtBidderConfigOrtb;
import org.prebid.server.proto.openrtb.ext.request.ExtDooh;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidBidderConfig;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidData;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.request.Targeting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

public class FpdResolver {

    private static final String USER = "user";
    private static final String SITE = "site";
    private static final String BIDDERS = "bidders";
    private static final String APP = "app";
    private static final String DOOH = "dooh";
    private static final Set<String> KNOWN_FPD_ATTRIBUTES = Set.of(USER, SITE, APP, DOOH, BIDDERS);
    private static final String ALLOW_ALL_BIDDERS = "*";
    private static final String EXT = "ext";
    private static final String CONTEXT = "context";
    private static final String DATA = "data";
    private static final Set<String> USER_DATA_ATTR = Collections.singleton("geo");
    private static final Set<String> APP_DATA_ATTR = Set.of("id", "content", "publisher", "privacypolicy");
    private static final Set<String> SITE_DATA_ATTR = Set.of("id", "content", "publisher", "privacypolicy", "mobile");
    private static final Set<String> DOOH_DATA_ATTR = Set.of("id", "content", "publisher", "privacypolicy");

    private static final TypeReference<List<Data>> USER_DATA_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final JacksonMapper jacksonMapper;
    private final JsonMerger jsonMerger;

    public FpdResolver(JacksonMapper jacksonMapper, JsonMerger jsonMerger) {
        this.jacksonMapper = Objects.requireNonNull(jacksonMapper);
        this.jsonMerger = Objects.requireNonNull(jsonMerger);
    }

    public User resolveUser(User originUser, ObjectNode fpdUser) {
        if (fpdUser == null) {
            return originUser;
        }
        final User resultUser = originUser == null ? User.builder().build() : originUser;
        final ExtUser resolvedExtUser = resolveUserExt(fpdUser, resultUser);
        return resultUser.toBuilder()
                .keywords(ObjectUtils.defaultIfNull(getString(fpdUser, "keywords"), resultUser.getKeywords()))
                .gender(ObjectUtils.defaultIfNull(getString(fpdUser, "gender"), resultUser.getGender()))
                .yob(ObjectUtils.defaultIfNull(getInteger(fpdUser, "yob"), resultUser.getYob()))
                .data(ObjectUtils.defaultIfNull(getFpdUserData(fpdUser), resultUser.getData()))
                .ext(resolvedExtUser)
                .build();
    }

    private ExtUser resolveUserExt(ObjectNode fpdUser, User originUser) {
        final ExtUser originExtUser = originUser.getExt();
        final ObjectNode resolvedData =
                mergeExtData(fpdUser.path(EXT).path(DATA), originExtUser != null ? originExtUser.getData() : null);

        return updateUserExtDataWithFpdAttr(fpdUser, originExtUser, resolvedData);
    }

    private ExtUser updateUserExtDataWithFpdAttr(ObjectNode fpdUser, ExtUser originExtUser, ObjectNode extData) {
        final ObjectNode resultData = extData != null ? extData : jacksonMapper.mapper().createObjectNode();
        USER_DATA_ATTR.forEach(attribute -> setAttr(fpdUser, resultData, attribute));
        return originExtUser != null
                ? originExtUser.toBuilder().data(resultData.isEmpty() ? null : resultData).build()
                : resultData.isEmpty() ? null : ExtUser.builder().data(resultData).build();
    }

    private List<Data> getFpdUserData(ObjectNode fpdUser) {
        final ArrayNode fpdUserDataNode = getValueFromJsonNode(
                fpdUser, DATA, node -> (ArrayNode) node, JsonNode::isArray);

        return toList(fpdUserDataNode, USER_DATA_TYPE_REFERENCE);
    }

    public App resolveApp(App originApp, ObjectNode fpdApp) {
        if (fpdApp == null) {
            return originApp;
        }
        final App resultApp = originApp == null ? App.builder().build() : originApp;
        final ExtApp resolvedExtApp = resolveAppExt(fpdApp, resultApp);
        return resultApp.toBuilder()
                .name(ObjectUtils.defaultIfNull(getString(fpdApp, "name"), resultApp.getName()))
                .bundle(ObjectUtils.defaultIfNull(getString(fpdApp, "bundle"), resultApp.getBundle()))
                .storeurl(ObjectUtils.defaultIfNull(getString(fpdApp, "storeurl"), resultApp.getStoreurl()))
                .domain(ObjectUtils.defaultIfNull(getString(fpdApp, "domain"), resultApp.getDomain()))
                .cat(ObjectUtils.defaultIfNull(getStrings(fpdApp, "cat"), resultApp.getCat()))
                .sectioncat(ObjectUtils.defaultIfNull(getStrings(fpdApp, "sectioncat"), resultApp.getSectioncat()))
                .pagecat(ObjectUtils.defaultIfNull(getStrings(fpdApp, "pagecat"), resultApp.getPagecat()))
                .keywords(ObjectUtils.defaultIfNull(getString(fpdApp, "keywords"), resultApp.getKeywords()))
                .ext(resolvedExtApp)
                .build();
    }

    private ExtApp resolveAppExt(ObjectNode fpdApp, App originApp) {
        final ExtApp originExtApp = originApp.getExt();
        final ObjectNode resolvedData =
                mergeExtData(fpdApp.path(EXT).path(DATA), originExtApp != null ? originExtApp.getData() : null);

        return updateAppExtDataWithFpdAttr(fpdApp, originExtApp, resolvedData);
    }

    private ExtApp updateAppExtDataWithFpdAttr(ObjectNode fpdApp, ExtApp originExtApp, ObjectNode extData) {
        final ObjectNode resultData = extData != null ? extData : jacksonMapper.mapper().createObjectNode();
        APP_DATA_ATTR.forEach(attribute -> setAttr(fpdApp, resultData, attribute));
        return originExtApp != null
                ? ExtApp.of(originExtApp.getPrebid(), resultData.isEmpty() ? null : resultData)
                : resultData.isEmpty() ? null : ExtApp.of(null, resultData);
    }

    public Site resolveSite(Site originSite, ObjectNode fpdSite) {
        if (fpdSite == null) {
            return originSite;
        }
        final Site resultSite = originSite == null ? Site.builder().build() : originSite;
        final ExtSite resolvedExtSite = resolveSiteExt(fpdSite, resultSite);
        return resultSite.toBuilder()
                .name(ObjectUtils.defaultIfNull(getString(fpdSite, "name"), resultSite.getName()))
                .domain(ObjectUtils.defaultIfNull(getString(fpdSite, "domain"), resultSite.getDomain()))
                .cat(ObjectUtils.defaultIfNull(getStrings(fpdSite, "cat"), resultSite.getCat()))
                .sectioncat(ObjectUtils.defaultIfNull(getStrings(fpdSite, "sectioncat"), resultSite.getSectioncat()))
                .pagecat(ObjectUtils.defaultIfNull(getStrings(fpdSite, "pagecat"), resultSite.getPagecat()))
                .page(ObjectUtils.defaultIfNull(getString(fpdSite, "page"), resultSite.getPage()))
                .keywords(ObjectUtils.defaultIfNull(getString(fpdSite, "keywords"), resultSite.getKeywords()))
                .ref(ObjectUtils.defaultIfNull(getString(fpdSite, "ref"), resultSite.getRef()))
                .search(ObjectUtils.defaultIfNull(getString(fpdSite, "search"), resultSite.getSearch()))
                .ext(resolvedExtSite)
                .build();
    }

    private ExtSite resolveSiteExt(ObjectNode fpdSite, Site originSite) {
        final ExtSite originExtSite = originSite.getExt();
        final ObjectNode resolvedData =
                mergeExtData(fpdSite.path(EXT).path(DATA), originExtSite != null ? originExtSite.getData() : null);

        return updateSiteExtDataWithFpdAttr(fpdSite, originExtSite, resolvedData);
    }

    private ExtSite updateSiteExtDataWithFpdAttr(ObjectNode fpdSite, ExtSite originExtSite, ObjectNode extData) {
        final ObjectNode resultData = extData != null ? extData : jacksonMapper.mapper().createObjectNode();
        SITE_DATA_ATTR.forEach(attribute -> setAttr(fpdSite, resultData, attribute));
        return originExtSite != null
                ? ExtSite.of(originExtSite.getAmp(), resultData.isEmpty() ? null : resultData)
                : resultData.isEmpty() ? null : ExtSite.of(null, resultData);
    }

    public Dooh resolveDooh(Dooh originDooh, ObjectNode fpdDooh) {
        if (fpdDooh == null) {
            return originDooh;
        }
        final Dooh resultDooh = originDooh == null ? Dooh.builder().build() : originDooh;
        final ExtDooh resolvedExtDooh = resolveDoohExt(fpdDooh, resultDooh);
        return resultDooh.toBuilder()
                .name(ObjectUtils.defaultIfNull(getString(fpdDooh, "name"), resultDooh.getName()))
                .venuetype(ObjectUtils.defaultIfNull(getStrings(fpdDooh, "venuetype"), resultDooh.getVenuetype()))
                .venuetypetax(ObjectUtils.defaultIfNull(
                        getInteger(fpdDooh, "venuetypetax"),
                        resultDooh.getVenuetypetax()))
                .domain(ObjectUtils.defaultIfNull(getString(fpdDooh, "domain"), resultDooh.getDomain()))
                .keywords(ObjectUtils.defaultIfNull(getString(fpdDooh, "keywords"), resultDooh.getKeywords()))
                .ext(resolvedExtDooh)
                .build();
    }

    private ExtDooh resolveDoohExt(ObjectNode fpdDooh, Dooh originDooh) {
        final ExtDooh originExtDooh = originDooh.getExt();
        final ObjectNode resolvedData =
                mergeExtData(fpdDooh.path(EXT).path(DATA), originExtDooh != null ? originExtDooh.getData() : null);

        return updateDoohExtDataWithFpdAttr(fpdDooh, originExtDooh, resolvedData);
    }

    private ExtDooh updateDoohExtDataWithFpdAttr(ObjectNode fpdDooh, ExtDooh originExtDooh, ObjectNode extData) {
        final ObjectNode resultData = extData != null ? extData : jacksonMapper.mapper().createObjectNode();
        DOOH_DATA_ATTR.forEach(attribute -> setAttr(fpdDooh, resultData, attribute));
        return originExtDooh != null
                ? ExtDooh.of(resultData.isEmpty() ? null : resultData)
                : resultData.isEmpty() ? null : ExtDooh.of(resultData);
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
                ExtBidderConfig.of(null, ExtBidderConfigOrtb.of(siteNode, null, null, userNode))));
    }

    private ObjectNode mergeExtData(JsonNode fpdData, JsonNode originData) {
        if (fpdData.isMissingNode() || !fpdData.isObject()) {
            return originData != null && originData.isObject() ? ((ObjectNode) originData).deepCopy() : null;
        }

        if (originData != null && originData.isObject()) {
            return (ObjectNode) jsonMerger.merge(fpdData, originData);
        }
        return fpdData.isObject() ? (ObjectNode) fpdData : null;
    }

    private static void setAttr(ObjectNode source, ObjectNode dest, String fieldName) {
        final JsonNode field = source.get(fieldName);
        if (field != null) {
            dest.set(fieldName, field);
        }
    }

    private static List<String> getStrings(JsonNode firstItem, String fieldName) {
        final JsonNode valueNode = firstItem.get(fieldName);
        final ArrayNode arrayNode = valueNode != null && valueNode.isArray() ? (ArrayNode) valueNode : null;
        return arrayNode != null && isTextualArray(arrayNode)
                ? StreamSupport.stream(arrayNode.spliterator(), false)
                .map(JsonNode::asText)
                .toList()
                : null;
    }

    private static boolean isTextualArray(ArrayNode arrayNode) {
        return StreamSupport.stream(arrayNode.spliterator(), false).allMatch(JsonNode::isTextual);
    }

    private static String getString(ObjectNode firstItem, String fieldName) {
        return getValueFromJsonNode(firstItem, fieldName, JsonNode::asText, JsonNode::isTextual);
    }

    private static Integer getInteger(ObjectNode firstItem, String fieldName) {
        return getValueFromJsonNode(firstItem, fieldName, JsonNode::asInt, JsonNode::isInt);
    }

    private <T> List<T> toList(JsonNode node, TypeReference<List<T>> listTypeReference) {
        try {
            return jacksonMapper.mapper().convertValue(node, listTypeReference);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static <T> T getValueFromJsonNode(ObjectNode firstItem, String fieldName,
                                              Function<JsonNode, T> nodeConverter,
                                              Predicate<JsonNode> isCorrectType) {
        final JsonNode valueNode = firstItem.get(fieldName);
        return valueNode != null && isCorrectType.test(valueNode)
                ? nodeConverter.apply(valueNode)
                : null;
    }

}
