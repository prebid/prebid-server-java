package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtBidderConfig;
import org.prebid.server.proto.openrtb.ext.request.ExtBidderConfigFpd;
import org.prebid.server.proto.openrtb.ext.request.ExtImp;
import org.prebid.server.proto.openrtb.ext.request.ExtImpContext;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidBidderConfig;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidData;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.request.Targeting;
import org.prebid.server.util.JsonMergeUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class FpdResolver {

    private static final String USER = "user";
    private static final String SITE = "site";
    private static final String BIDDERS = "bidders";
    private static final String APP = "app";
    private static final Set<String> KNOWN_FPD_ATTRIBUTES = new HashSet<>(Arrays.asList(USER, SITE, APP, BIDDERS));
    private static final String ALLOW_ALL_BIDDERS = "*";
    private static final String EXT = "ext";
    private static final String DATA = "data";
    private static final Set<String> USER_DATA_ATTR = Collections.singleton("geo");
    private static final Set<String> APP_DATA_ATTR = new HashSet<>(Arrays.asList("id", "content", "publisher",
            "privacypolicy"));
    private static final Set<String> SITE_DATA_ATTR = new HashSet<>(Arrays.asList("id", "content", "publisher",
            "privacypolicy", "mobile"));

    private final JacksonMapper jacksonMapper;
    private final JsonMergeUtil jsonMergeUtil;

    public FpdResolver(JacksonMapper jacksonMapper) {
        this.jacksonMapper = Objects.requireNonNull(jacksonMapper);

        this.jsonMergeUtil = new JsonMergeUtil(jacksonMapper);
    }

    public User resolveUser(User originUser, ObjectNode fpdUser) {
        if (fpdUser == null) {
            return originUser;
        }
        final User resultUser = originUser == null ? User.builder().build() : originUser;
        final ExtUser resolvedExtUser = resolveUserExt(fpdUser, resultUser);
        return resultUser.toBuilder()
                .keywords(getFirstNotNull(getString(fpdUser, "keywords"), resultUser.getKeywords()))
                .gender(getFirstNotNull(getString(fpdUser, "gender"), resultUser.getGender()))
                .yob(getFirstNotNull(getInteger(fpdUser, "yob"), resultUser.getYob()))
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

        ObjectNode resultData = extData != null ? extData : jacksonMapper.mapper().createObjectNode();
        USER_DATA_ATTR.forEach(attribute -> setAttr(fpdUser, resultData, attribute));
        return originExtUser != null
                ? originExtUser.toBuilder().data(resultData.isEmpty() ? null : resultData).build()
                : resultData.isEmpty() ? null : ExtUser.builder().data(resultData).build();
    }

    public App resolveApp(App originApp, ObjectNode fpdApp) {
        if (fpdApp == null) {
            return originApp;
        }
        final App resultApp = originApp == null ? App.builder().build() : originApp;
        final ExtApp resolvedExtApp = resolveAppExt(fpdApp, resultApp);
        return resultApp.toBuilder()
                .name(getFirstNotNull(getString(fpdApp, "name"), resultApp.getName()))
                .bundle(getFirstNotNull(getString(fpdApp, "bundle"), resultApp.getName()))
                .storeurl(getFirstNotNull(getString(fpdApp, "storeurl"), resultApp.getStoreurl()))
                .domain(getFirstNotNull(getString(fpdApp, "domain"), resultApp.getDomain()))
                .cat(getFirstNotNull(getStrings(fpdApp, "cat"), resultApp.getCat()))
                .sectioncat(getFirstNotNull(getStrings(fpdApp, "sectioncat"), resultApp.getSectioncat()))
                .pagecat(getFirstNotNull(getStrings(fpdApp, "pagecat"), resultApp.getPagecat()))
                .keywords(getFirstNotNull(getString(fpdApp, "keywords"), resultApp.getKeywords()))
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
                .name(getFirstNotNull(getString(fpdSite, "name"), resultSite.getName()))
                .domain(getFirstNotNull(getString(fpdSite, "domain"), resultSite.getDomain()))
                .cat(getFirstNotNull(getStrings(fpdSite, "cat"), resultSite.getCat()))
                .sectioncat(getFirstNotNull(getStrings(fpdSite, "sectioncat"), resultSite.getSectioncat()))
                .pagecat(getFirstNotNull(getStrings(fpdSite, "pagecat"), resultSite.getPagecat()))
                .page(getFirstNotNull(getString(fpdSite, "page"), resultSite.getPage()))
                .keywords(getFirstNotNull(getString(fpdSite, "keywords"), resultSite.getKeywords()))
                .ref(getFirstNotNull(getString(fpdSite, "ref"), resultSite.getRef()))
                .search(getFirstNotNull(getString(fpdSite, "search"), resultSite.getRef()))
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

    public ObjectNode resolveImpExt(ObjectNode impExt, ObjectNode targeting) {
        if (targeting == null) {
            return impExt;
        }

        KNOWN_FPD_ATTRIBUTES.forEach(targeting::remove);
        if (!targeting.fieldNames().hasNext()) {
            return impExt;
        }

        final ExtImp extImp = impExt != null ? getExtImp(impExt) : null;
        final ExtImpContext extImpContext = extImp != null ? extImp.getContext() : null;
        final ObjectNode extImpContextData = extImpContext != null ? extImpContext.getData() : null;
        final ObjectNode resolvedData = extImpContextData != null
                ? (ObjectNode) jsonMergeUtil.merge(targeting, extImpContextData)
                : targeting;
        final ExtImp resolvedExtImp = ExtImp.of(extImp != null ? extImp.getPrebid() : null,
                extImpContext != null
                        ? ExtImpContext.of(
                        extImpContext.getKeywords(),
                        extImpContext.getSearch(),
                        extImpContext.getAdserver(),
                        resolvedData)
                        : ExtImpContext.of(null, null, null, resolvedData));

        return jacksonMapper.mapper().valueToTree(resolvedExtImp);
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
                    .bidderconfig(resolvedBidderConfig).build());
        }

        return extRequest;
    }

    private ExtRequestPrebidData resolveExtRequestPrebidData(ExtRequestPrebidData data, List<String> fpdBidders) {
        if (CollectionUtils.isEmpty(fpdBidders) && data == null) {
            return null;
        }
        final List<String> originBidders = data != null ? data.getBidders() : Collections.emptyList();
        return CollectionUtils.isEmpty(originBidders)
                ? ExtRequestPrebidData.of(fpdBidders)
                : ExtRequestPrebidData.of(mergeBidders(fpdBidders, originBidders));
    }

    private List<ExtRequestPrebidBidderConfig> createAllowedAllBidderConfig(Targeting targeting) {
        final ObjectNode userNode = targeting.getUser();
        final ObjectNode siteNode = targeting.getSite();
        if (userNode == null && siteNode == null) {
            return null;
        }
        final List<String> bidders = Collections.singletonList(ALLOW_ALL_BIDDERS);

        return Collections.singletonList(ExtRequestPrebidBidderConfig.of(bidders,
                ExtBidderConfig.of(ExtBidderConfigFpd.of(
                        moveDataAttributesToExtData(siteNode), null, moveDataAttributesToExtData(userNode)))));
    }

    private ObjectNode moveDataAttributesToExtData(ObjectNode source) {
        final JsonNode dataNode = source != null ? source.get(DATA) : null;
        if (dataNode != null) {
            source.set(EXT, jacksonMapper.mapper().createObjectNode().set(DATA, dataNode));
            source.remove(DATA);
        }
        return source;
    }

    private List<String> mergeBidders(List<String> fpdBidders, List<String> originBidders) {
        final HashSet<String> resolvedBidders = new HashSet<>(originBidders);
        resolvedBidders.addAll(fpdBidders);
        return new ArrayList<>(resolvedBidders);
    }

    private ObjectNode mergeExtData(JsonNode fpdData, JsonNode originData) {
        if (fpdData.isMissingNode() || !fpdData.isObject()) {
            return originData != null && originData.isObject() ? (ObjectNode) originData : null;
        }

        if (originData != null && originData.isObject()) {
            return (ObjectNode) jsonMergeUtil.merge(fpdData, originData);
        }
        return fpdData.isObject() ? (ObjectNode) fpdData : null;
    }

    private ExtImp getExtImp(ObjectNode extImp) {
        try {
            return jacksonMapper.mapper().treeToValue(extImp, ExtImp.class);
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException(String.format("Failed to decode imp.ext: %s", e.getMessage()));
        }
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
                .collect(Collectors.toList())
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

    private static <T> T getValueFromJsonNode(ObjectNode firstItem, String fieldName,
                                              Function<JsonNode, T> nodeConverter,
                                              Predicate<JsonNode> isCorrectType) {
        final JsonNode valueNode = firstItem.get(fieldName);
        return valueNode != null && isCorrectType.test(valueNode)
                ? nodeConverter.apply(valueNode)
                : null;
    }

    private static <T> T getFirstNotNull(T firstValue, T secondValue) {
        return firstValue != null ? firstValue : secondValue;
    }
}
