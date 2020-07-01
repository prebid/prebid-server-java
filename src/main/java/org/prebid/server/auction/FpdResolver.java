package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtImp;
import org.prebid.server.proto.openrtb.ext.request.ExtImpContext;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidData;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
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

    public User resolveUser(User originUser, User fpdUser) {
        if (originUser == null) {
            return null;
        }
        if (fpdUser == null) {
            return originUser;
        }
        final ExtUser originExtUser = originUser.getExt();
        final ObjectNode resolvedData = mergeExtData(originExtUser, fpdUser.getExt(),
                extUser -> extUser != null ? extUser.getData() : null);

        final ExtUser resolvedExtUser = originExtUser != null
                ? originExtUser.toBuilder().data(resolvedData).build()
                : ExtUser.builder().data(resolvedData).build();

        return originUser.toBuilder()
                .keywords(getFirstNotNull(fpdUser, originUser, User::getKeywords))
                .gender(getFirstNotNull(fpdUser, originUser, User::getGender))
                .yob(getFirstNotNull(fpdUser, originUser, User::getYob))
                .ext(resolvedExtUser)
                .build();
    }

    public App resolveApp(App originApp, App fpdApp) {
        if (originApp == null) {
            return null;
        }
        if (fpdApp == null) {
            return originApp;
        }
        final ExtApp originExtApp = originApp.getExt();
        final ObjectNode resolvedData = mergeExtData(originExtApp, fpdApp.getExt(),
                extApp -> extApp != null ? extApp.getData() : null);

        final ExtApp resolvedExtApp = originExtApp != null
                ? ExtApp.of(originExtApp.getPrebid(), resolvedData)
                : ExtApp.of(null, resolvedData);

        return originApp.toBuilder()
                .id(getFirstNotNull(fpdApp, originApp, App::getId))
                .name(getFirstNotNull(fpdApp, originApp, App::getName))
                .bundle(getFirstNotNull(fpdApp, originApp, App::getBundle))
                .storeurl(getFirstNotNull(fpdApp, originApp, App::getStoreurl))
                .domain(getFirstNotNull(fpdApp, originApp, App::getDomain))
                .cat(getFirstNotNull(fpdApp, originApp, App::getCat))
                .sectioncat(getFirstNotNull(fpdApp, originApp, App::getSectioncat))
                .pagecat(getFirstNotNull(fpdApp, originApp, App::getPagecat))
                .content(getFirstNotNull(fpdApp, originApp, App::getContent))
                .publisher(getFirstNotNull(fpdApp, originApp, App::getPublisher))
                .keywords(getFirstNotNull(fpdApp, originApp, App::getKeywords))
                .privacypolicy(getFirstNotNull(fpdApp, originApp, App::getPrivacypolicy))
                .ext(resolvedExtApp)
                .build();
    }

    public Site resolveSite(Site originSite, Site fpdSite) {
        if (originSite == null) {
            return null;
        }
        if (fpdSite == null) {
            return originSite;
        }

        final ExtSite originExtSite = originSite.getExt();
        final ObjectNode resolvedData = mergeExtData(originExtSite, fpdSite.getExt(),
                extSite -> extSite != null ? extSite.getData() : null);

        final ExtSite resolvedExApp = originExtSite != null
                ? ExtSite.of(originExtSite.getAmp(), resolvedData)
                : ExtSite.of(null, resolvedData);

        return originSite.toBuilder()
                .id(getFirstNotNull(fpdSite, originSite, Site::getId))
                .name(getFirstNotNull(fpdSite, originSite, Site::getName))
                .domain(getFirstNotNull(fpdSite, originSite, Site::getDomain))
                .cat(getFirstNotNull(fpdSite, originSite, Site::getCat))
                .sectioncat(getFirstNotNull(fpdSite, originSite, Site::getSectioncat))
                .pagecat(getFirstNotNull(fpdSite, originSite, Site::getPagecat))
                .page(getFirstNotNull(fpdSite, originSite, Site::getPage))
                .ref(getFirstNotNull(fpdSite, originSite, Site::getRef))
                .search(getFirstNotNull(fpdSite, originSite, Site::getSearch))
                .content(getFirstNotNull(fpdSite, originSite, Site::getContent))
                .publisher(getFirstNotNull(fpdSite, originSite, Site::getPublisher))
                .keywords(getFirstNotNull(fpdSite, originSite, Site::getKeywords))
                .mobile(getFirstNotNull(fpdSite, originSite, Site::getMobile))
                .privacypolicy(getFirstNotNull(fpdSite, originSite, Site::getPrivacypolicy))
                .ext(resolvedExApp)
                .build();
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
        final ObjectNode data = extImpContext != null ? extImpContext.getData() : null;
        final ObjectNode resolvedData = (ObjectNode) jsonMergeUtil.merge(targeting, data);
        final ExtImp resolvedExtImp = ExtImp.of(extImp != null ? extImp.getPrebid() : null,
                extImpContext != null
                        ? ExtImpContext.of(extImpContext.getKeywords(), extImpContext.getSearch(), resolvedData)
                        : ExtImpContext.of(null, null, resolvedData));

        return mapper.mapper().valueToTree(resolvedExtImp);
    }

    public ExtRequest resolveBidRequestExt(ExtRequest extRequest, List<String> fpdBidders) {
        final ExtRequestPrebid extRequestPrebid = extRequest != null ? extRequest.getPrebid() : null;
        final ExtRequestPrebidData extRequestPrebidData = extRequestPrebid != null ? extRequestPrebid.getData() : null;
        final List<String> originBidders = extRequestPrebidData != null
                ? extRequestPrebidData.getBidders()
                : Collections.emptyList();

        final List<String> resolvedBidders = mergeBidders(fpdBidders, originBidders);

        return ExtRequest.of(extRequestPrebid != null
                ? extRequestPrebid.toBuilder().data(ExtRequestPrebidData.of(resolvedBidders)).build()
                : ExtRequestPrebid.builder().data(ExtRequestPrebidData.of(resolvedBidders)).build());
    }

    private List<String> mergeBidders(List<String> fpdBidders, List<String> originBidders) {
        final HashSet<String> resolvedBidders = new HashSet<>(originBidders);
        resolvedBidders.addAll(fpdBidders);
        return new ArrayList<>(resolvedBidders);
    }

    private <T> ObjectNode mergeExtData(T originExt, T fpdExt, Function<T, ObjectNode> dataRetriever) {
        final ObjectNode fpdData = dataRetriever.apply(fpdExt);
        final ObjectNode originData = dataRetriever.apply(originExt);

        if (fpdData == null) {
            return originData;
        }
        if (originData != null) {
            return (ObjectNode) jsonMergeUtil.merge(originData, fpdData);
        }
        return fpdData;
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
