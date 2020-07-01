package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.ExtBidRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtImp;
import org.prebid.server.proto.openrtb.ext.request.ExtImpContext;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidData;
import org.prebid.server.util.JsonMergeUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

public class FpdResolver {

    private static final String CONTEXT = "context";
    private static final String DATA = "data";
    private static final String USER = "user";
    private static final String SITE = "site";
    private static final String BIDDERS = "bidders";
    private static final Set<String> KNOWN_FPD_ATTRIBUTES = new HashSet<>(Arrays.asList(USER, SITE, BIDDERS));

    private final JacksonMapper mapper;
    private final JsonMergeUtil jsonMergeUtil;

    public FpdResolver(JacksonMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);

        this.jsonMergeUtil = new JsonMergeUtil(mapper);
    }

    public User resolveUser(User originalUser, User fpdUser) {
        return originalUser.toBuilder()
                .keywords(getFirstNotNull(fpdUser, originalUser, User::getKeywords))
                .gender(getFirstNotNull(fpdUser, originalUser, User::getGender))
                .yob(getFirstNotNull(fpdUser, originalUser, User::getYob))
                .ext((ObjectNode) mergeExtData(originalUser.getExt(), fpdUser.getExt()))
                .build();
    }

    public App resolveApp(App originalApp, App fpdApp) {
        return originalApp.toBuilder()
                .id(getFirstNotNull(fpdApp, originalApp, App::getId))
                .name(getFirstNotNull(fpdApp, originalApp, App::getName))
                .bundle(getFirstNotNull(fpdApp, originalApp, App::getBundle))
                .storeurl(getFirstNotNull(fpdApp, originalApp, App::getStoreurl))
                .domain(getFirstNotNull(fpdApp, originalApp, App::getDomain))
                .cat(getFirstNotNull(fpdApp, originalApp, App::getCat))
                .sectioncat(getFirstNotNull(fpdApp, originalApp, App::getSectioncat))
                .pagecat(getFirstNotNull(fpdApp, originalApp, App::getPagecat))
                .content(getFirstNotNull(fpdApp, originalApp, App::getContent))
                .publisher(getFirstNotNull(fpdApp, originalApp, App::getPublisher))
                .keywords(getFirstNotNull(fpdApp, originalApp, App::getKeywords))
                .privacypolicy(getFirstNotNull(fpdApp, originalApp, App::getPrivacypolicy))
                .ext((ObjectNode) mergeExtData(originalApp.getExt(), fpdApp.getExt()))
                .build();
    }

    public Site resolveSite(Site originalSite, Site fpdSite) {
        return originalSite.toBuilder()
                .id(getFirstNotNull(fpdSite, originalSite, Site::getId))
                .name(getFirstNotNull(fpdSite, originalSite, Site::getName))
                .domain(getFirstNotNull(fpdSite, originalSite, Site::getDomain))
                .cat(getFirstNotNull(fpdSite, originalSite, Site::getCat))
                .sectioncat(getFirstNotNull(fpdSite, originalSite, Site::getSectioncat))
                .pagecat(getFirstNotNull(fpdSite, originalSite, Site::getPagecat))
                .page(getFirstNotNull(fpdSite, originalSite, Site::getPage))
                .ref(getFirstNotNull(fpdSite, originalSite, Site::getRef))
                .search(getFirstNotNull(fpdSite, originalSite, Site::getSearch))
                .content(getFirstNotNull(fpdSite, originalSite, Site::getContent))
                .publisher(getFirstNotNull(fpdSite, originalSite, Site::getPublisher))
                .keywords(getFirstNotNull(fpdSite, originalSite, Site::getKeywords))
                .mobile(getFirstNotNull(fpdSite, originalSite, Site::getMobile))
                .privacypolicy(getFirstNotNull(fpdSite, originalSite, Site::getPrivacypolicy))
                .ext((ObjectNode) mergeExtData(originalSite.getExt(), fpdSite.getExt()))
                .build();
    }

    public ObjectNode resolveImpExt(ObjectNode impExt, ObjectNode targeting) {
        KNOWN_FPD_ATTRIBUTES.forEach(targeting::remove);
        if (!targeting.fieldNames().hasNext()) {
            return impExt;
        }

        final ExtImp extImp = impExt != null ? getExtImp(impExt) : null;
        final ExtImpContext extImpContext = extImp != null ? extImp.getContext() : null;
        final ObjectNode data = extImpContext != null ? extImpContext.getData() : null;
        final ObjectNode resolvedData = (ObjectNode) jsonMergeUtil.merge(targeting, data);
        final ExtImp resolvedExtImp = ExtImp.of(extImp != null ? extImp.getPrebid() : null,
                extImpContext != null
                        ? ExtImpContext.of(extImpContext.getKeywords(), extImpContext.getSearch(), resolvedData)
                        : ExtImpContext.of(null, null, resolvedData));

        return mapper.mapper().valueToTree(resolvedExtImp);
    }

    public ObjectNode resolveBidRequestExt(ObjectNode bidRequestExt, List<String> fpdBidders) {
        final ExtBidRequest extBidRequest = bidRequestExt != null ? convertToExtBidRequest(bidRequestExt) : null;
        final ExtRequestPrebid extRequestPrebid = extBidRequest != null ? extBidRequest.getPrebid() : null;
        final ExtRequestPrebidData extRequestPrebidData = extRequestPrebid != null ? extRequestPrebid.getData() : null;
        final List<String> ortbBidders = extRequestPrebidData != null
                ? extRequestPrebidData.getBidders()
                : Collections.emptyList();

        final List<String> resolvedBidders = mergeBidders(fpdBidders, ortbBidders);

        final ExtBidRequest resolvedExtBidRequest = ExtBidRequest.of(extRequestPrebid != null
                ? extRequestPrebid.toBuilder().data(ExtRequestPrebidData.of(resolvedBidders)).build()
                : ExtRequestPrebid.builder().data(ExtRequestPrebidData.of(resolvedBidders)).build());
        return mapper.mapper().valueToTree(resolvedExtBidRequest);
    }

    private List<String> mergeBidders(List<String> fpdBidders, List<String> ortbBidders) {
        final HashSet<String> resolvedBidders = new HashSet<>(ortbBidders);
        resolvedBidders.addAll(fpdBidders);
        return new ArrayList<>(resolvedBidders);
    }

    private JsonNode mergeExtData(ObjectNode ortbExtNode, ObjectNode fpdExtNode) {
        final JsonNode fpdDataExtNode = fpdExtNode != null ? fpdExtNode.get(DATA) : null;
        final JsonNode ortbDataExtNode = ortbExtNode != null ? ortbExtNode.get(DATA) : null;
        if (fpdDataExtNode != null) {
            if (ortbDataExtNode != null) {
                ortbExtNode.set(DATA, jsonMergeUtil.merge(fpdExtNode, ortbExtNode));
            } else {
                if (ortbExtNode != null) {
                    ortbExtNode.set(DATA, fpdDataExtNode);
                } else {
                    mapper.mapper().createObjectNode().set(DATA, fpdDataExtNode);
                }
            }
        }
        return ortbExtNode;
    }

    private ExtBidRequest convertToExtBidRequest(ObjectNode extBidRequest) {
        try {
            return mapper.mapper().treeToValue(extBidRequest, ExtBidRequest.class);
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException(String.format("Error converting bidRequest.ext : %s", e.getMessage()));
        }
    }

    private ExtImp getExtImp(ObjectNode extImp) {
        try {
            return mapper.mapper().treeToValue(extImp, ExtImp.class);
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException(String.format("Failed to decode imp.ext: %s", e.getMessage()));
        }
    }

    private static <T, R> R getFirstNotNull(T firstItem, T secondItem, Function<T, R> getter) {
        final R firstValue = getter.apply(firstItem);
        return firstValue != null ? firstValue : getter.apply(secondItem);
    }
}
