package org.prebid.server.bidder.smaato;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Dooh;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.Endpoint;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.smaato.proto.SmaatoBidExt;
import org.prebid.server.bidder.smaato.proto.SmaatoBidRequestExt;
import org.prebid.server.bidder.smaato.proto.SmaatoNativeAd;
import org.prebid.server.bidder.smaato.proto.SmaatoSiteExtData;
import org.prebid.server.bidder.smaato.proto.SmaatoUserExtData;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.EncodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidServer;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.smaato.ExtImpSmaato;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidVideo;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class SmaatoBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpSmaato>> SMAATO_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final String CLIENT_VERSION = "prebid_server_1.1";
    private static final String SMT_ADTYPE_HEADER = "X-Smt-Adtype";
    private static final String SMT_EXPIRES_HEADER = "X-Smt-Expires";
    private static final String SMT_AD_TYPE_IMG = "Img";
    private static final String SMT_ADTYPE_RICHMEDIA = "Richmedia";
    private static final String SMT_ADTYPE_VIDEO = "Video";
    private static final String SMT_ADTYPE_NATIVE = "Native";
    private static final String IMP_EXT_SKADN_FIELD = "skadn";

    private static final int DEFAULT_TTL = 300;

    private final String endpointUrl;
    private final JacksonMapper mapper;
    private final Clock clock;

    public SmaatoBidder(String endpointUrl, JacksonMapper mapper, Clock clock) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final BidRequest enrichedRequest;
        try {
            enrichedRequest = enrichRequestWithCommonProperties(request);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        final List<BidderError> errors = new ArrayList<>();
        if (isVideoRequest(request)) {
            return Result.of(constructPodRequests(enrichedRequest, errors), errors);
        }
        return Result.of(constructIndividualRequests(enrichedRequest, errors), errors);
    }

    private BidRequest enrichRequestWithCommonProperties(BidRequest bidRequest) {
        return bidRequest.toBuilder()
                .user(modifyUser(bidRequest.getUser()))
                .site(modifySite(bidRequest.getSite()))
                .ext(mapper.fillExtension(ExtRequest.empty(), SmaatoBidRequestExt.of(CLIENT_VERSION)))
                .build();
    }

    private User modifyUser(User user) {
        final ExtUser userExt = getIfNotNull(user, User::getExt);
        if (userExt == null) {
            return user;
        }

        final ObjectNode extDataNode = userExt.getData();
        if (extDataNode == null || extDataNode.isEmpty()) {
            return user;
        }

        final SmaatoUserExtData smaatoUserExtData = convertExt(extDataNode, SmaatoUserExtData.class);
        final User.UserBuilder userBuilder = user.toBuilder();

        final String gender = smaatoUserExtData.getGender();
        if (StringUtils.isNotBlank(gender)) {
            userBuilder.gender(gender);
        }

        final Integer yob = smaatoUserExtData.getYob();
        if (!BidderUtil.isNullOrZero(yob)) {
            userBuilder.yob(yob);
        }

        final String keywords = smaatoUserExtData.getKeywords();
        if (StringUtils.isNotBlank(keywords)) {
            userBuilder.keywords(keywords);
        }

        return userBuilder
                .ext(userExt.toBuilder().data(null).build())
                .build();
    }

    private Site modifySite(Site site) {
        if (site == null) {
            return null;
        }
        final ExtSite siteExt = getIfNotNull(site, Site::getExt);
        if (siteExt != null) {
            final SmaatoSiteExtData data = convertExt(siteExt.getData(), SmaatoSiteExtData.class);
            final String keywords = getIfNotNull(data, SmaatoSiteExtData::getKeywords);
            return site.toBuilder().keywords(keywords).ext(null).build();
        }
        return site;
    }

    private <T> T convertExt(ObjectNode ext, Class<T> className) {
        try {
            return mapper.mapper().convertValue(ext, className);
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Cannot decode extension: " + e.getMessage(), e);
        }
    }

    private static boolean isVideoRequest(BidRequest bidRequest) {
        final ExtRequestPrebid prebid = getIfNotNull(bidRequest.getExt(), ExtRequest::getPrebid);
        final ExtRequestPrebidServer server = getIfNotNull(prebid, ExtRequestPrebid::getServer);
        final String endpointName = getIfNotNull(server, ExtRequestPrebidServer::getEndpoint);

        return StringUtils.equals(endpointName, Endpoint.openrtb2_video.value());
    }

    private List<HttpRequest<BidRequest>> constructPodRequests(BidRequest bidRequest, List<BidderError> errors) {
        final List<Imp> validImps = new ArrayList<>();
        for (Imp imp : bidRequest.getImp()) {
            if (imp.getVideo() == null) {
                errors.add(BidderError.badInput("Invalid MediaType. Smaato only supports Video for AdPod."));
                continue;
            }
            validImps.add(imp);
        }

        return validImps.stream()
                .collect(Collectors.groupingBy(SmaatoBidder::extractPod, Collectors.toList()))
                .values().stream()
                .map(impsPod -> preparePodRequest(bidRequest, impsPod, errors))
                .filter(Objects::nonNull)
                .map(this::constructHttpRequest)
                .toList();
    }

    private static String extractPod(Imp imp) {
        return imp.getId().split("_")[0];
    }

    private BidRequest preparePodRequest(BidRequest bidRequest, List<Imp> imps, List<BidderError> errors) {
        try {
            final ObjectNode impExt = imps.getFirst().getExt();
            final ExtImpSmaato extImpSmaato =
                    mapper.mapper().convertValue(impExt, SMAATO_EXT_TYPE_REFERENCE).getBidder();
            final String publisherId = getIfNotNullOrThrow(extImpSmaato, ExtImpSmaato::getPublisherId, "publisherId");
            final String adBreakId = getIfNotNullOrThrow(extImpSmaato, ExtImpSmaato::getAdbreakId, "adbreakId");

            return modifyBidRequest(bidRequest, publisherId, () ->
                    modifyImpsForAdBreak(imps, adBreakId, resolveImpExtSkadn(impExt)));
        } catch (PreBidException | IllegalArgumentException e) {
            errors.add(BidderError.badInput(e.getMessage()));
            return null;
        }
    }

    private BidRequest modifyBidRequest(BidRequest bidRequest, String publisherId, Supplier<List<Imp>> impSupplier) {
        final Publisher publisher = Publisher.builder().id(publisherId).build();
        final Site site = bidRequest.getSite();
        final App app = bidRequest.getApp();
        final Dooh dooh = bidRequest.getDooh();

        final BidRequest.BidRequestBuilder bidRequestBuilder = bidRequest.toBuilder();
        if (site != null) {
            bidRequestBuilder.site(site.toBuilder().publisher(publisher).build());
        } else if (app != null) {
            bidRequestBuilder.app(app.toBuilder().publisher(publisher).build());
        } else if (dooh != null) {
            bidRequestBuilder.dooh(dooh.toBuilder().publisher(publisher).build());
        } else {
            throw new PreBidException("Missing Site/App/DOOH.");
        }

        return bidRequestBuilder.imp(impSupplier.get()).build();
    }

    private List<Imp> modifyImpsForAdBreak(List<Imp> imps, String adBreakId, ObjectNode impExtSkadn) {
        return IntStream.range(0, imps.size())
                .mapToObj(idx ->
                        modifyImpForAdBreak(imps.get(idx), idx + 1, adBreakId, idx == 0 ? impExtSkadn : null))
                .toList();
    }

    private Imp modifyImpForAdBreak(Imp imp, Integer sequence, String adBreakId, ObjectNode impExtSkadn) {
        final Video modifiedVideo = imp.getVideo().toBuilder()
                .sequence(sequence)
                .ext(mapper.mapper().createObjectNode().set("context", TextNode.valueOf("adpod")))
                .build();
        return imp.toBuilder()
                .tagid(adBreakId)
                .video(modifiedVideo)
                .ext(impExtSkadn)
                .build();
    }

    private List<HttpRequest<BidRequest>> constructIndividualRequests(BidRequest bidRequest, List<BidderError> errors) {
        return splitImps(bidRequest.getImp(), errors).stream()
                .map(imp -> prepareIndividualRequest(bidRequest, imp, errors))
                .filter(Objects::nonNull)
                .map(this::constructHttpRequest)
                .toList();
    }

    private List<Imp> splitImps(List<Imp> imps, List<BidderError> errors) {
        final List<Imp> splitImps = new ArrayList<>();

        for (Imp imp : imps) {
            final Banner banner = imp.getBanner();
            final Video video = imp.getVideo();
            final Native xNative = imp.getXNative();
            if (ObjectUtils.allNull(video, banner, xNative)) {
                errors.add(BidderError.badInput("Invalid MediaType. Smaato only supports Banner, Video and Native."));
                continue;
            }

            if (video != null) {
                splitImps.add(imp.toBuilder().banner(null).xNative(null).build());
            }
            if (banner != null) {
                splitImps.add(imp.toBuilder().video(null).xNative(null).build());
            }
            if (xNative != null) {
                splitImps.add(imp.toBuilder().banner(null).video(null).build());
            }
        }

        return splitImps;
    }

    private BidRequest prepareIndividualRequest(BidRequest bidRequest, Imp imp, List<BidderError> errors) {
        try {
            final ObjectNode impExt = imp.getExt();
            final ExtImpSmaato extImpSmaato =
                    mapper.mapper().convertValue(impExt, SMAATO_EXT_TYPE_REFERENCE).getBidder();
            final String publisherId = getIfNotNullOrThrow(extImpSmaato, ExtImpSmaato::getPublisherId, "publisherId");
            final String adSpaceId = getIfNotNullOrThrow(extImpSmaato, ExtImpSmaato::getAdspaceId, "adspaceId");

            return modifyBidRequest(bidRequest, publisherId, () ->
                    modifyImpForAdSpace(imp, adSpaceId, resolveImpExtSkadn(impExt)));
        } catch (PreBidException | IllegalArgumentException e) {
            errors.add(BidderError.badInput(e.getMessage()));
            return null;
        }
    }

    private ObjectNode resolveImpExtSkadn(ObjectNode impExt) {
        if (!impExt.has(IMP_EXT_SKADN_FIELD)) {
            return null;
        } else if (impExt.get(IMP_EXT_SKADN_FIELD).isEmpty() || !impExt.get(IMP_EXT_SKADN_FIELD).isObject()) {
            throw new PreBidException("Invalid imp.ext.skadn");
        } else {
            return mapper.mapper().createObjectNode().set(IMP_EXT_SKADN_FIELD, impExt.get(IMP_EXT_SKADN_FIELD));
        }
    }

    private List<Imp> modifyImpForAdSpace(Imp imp, String adSpaceId, ObjectNode impExtSkadn) {
        final Imp modifiedImp = imp.toBuilder()
                .tagid(adSpaceId)
                .ext(impExtSkadn)
                .build();

        return Collections.singletonList(modifiedImp);
    }

    private HttpRequest<BidRequest> constructHttpRequest(BidRequest bidRequest) {
        return BidderUtil.defaultRequest(bidRequest, endpointUrl, mapper);
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return extractBids(bidResponse, httpCall.getResponse().getHeaders());
        } catch (PreBidException | DecodeException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }
    }

    private Result<List<BidderBid>> extractBids(BidResponse bidResponse, MultiMap headers) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Result.empty();
        }

        final String markupType = getAdMarkupType(headers);
        final List<BidderError> errors = new ArrayList<>();
        final List<BidderBid> bidderBids = bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> bidderBid(bid, bidResponse.getCur(), markupType, headers, errors))
                .filter(Objects::nonNull)
                .toList();

        return Result.of(bidderBids, errors);
    }

    private BidderBid bidderBid(Bid bid,
                                String currency,
                                String markupType,
                                MultiMap headers,
                                List<BidderError> errors) {
        try {
            final String bidAdm = bid.getAdm();
            if (StringUtils.isBlank(bidAdm)) {
                throw new PreBidException("Empty ad markup in bid with id: " + bid.getId());
            }
            final SmaatoBidExt bidExt = parseBidExt(bid.getExt());
            final BidType bidType = getBidType(markupType);
            final Bid updatedBid = bid.toBuilder()
                    .adm(renderAdMarkup(markupType, bidAdm, bidExt))
                    .exp(getTtl(headers))
                    .build();
            return BidderBid.builder()
                    .bid(updatedBid)
                    .type(bidType)
                    .bidCurrency(currency)
                    .videoInfo(getExtBidPrebidVideo(bid, bidType, bidExt))
                    .build();
        } catch (PreBidException e) {
            errors.add(BidderError.badInput(e.getMessage()));
            return null;
        }
    }

    private ExtBidPrebidVideo getExtBidPrebidVideo(Bid bid, BidType bidType, SmaatoBidExt bidExt) {
        if (bidType != BidType.video) {
            return null;
        }

        final List<String> categories = bid.getCat();
        final String primaryCategory = CollectionUtils.isNotEmpty(categories) ? categories.getFirst() : null;
        try {
            return ExtBidPrebidVideo.of(bidExt.getDuration(), primaryCategory);
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Invalid bid.ext.");
        }
    }

    private SmaatoBidExt parseBidExt(ObjectNode bidExt) {
        try {
            final SmaatoBidExt parsedExt = mapper.mapper().convertValue(bidExt, SmaatoBidExt.class);
            return parsedExt == null ? SmaatoBidExt.empty() : parsedExt;
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Invalid bid.ext.");
        }
    }

    private int getTtl(MultiMap headers) {
        try {
            final long expiresAtMillis = Long.parseLong(headers.get(SMT_EXPIRES_HEADER));
            final long currentTimeMillis = clock.millis();
            return (int) Math.max((expiresAtMillis - currentTimeMillis) / 1000, 0);
        } catch (NumberFormatException e) {
            return DEFAULT_TTL;
        }
    }

    private static String getAdMarkupType(MultiMap headers) {
        final String adMarkupType = headers.get(SMT_ADTYPE_HEADER);
        if (StringUtils.isNotBlank(adMarkupType)) {
            return adMarkupType;
        }
        throw new PreBidException("X-Smt-Adtype header is missing.");
    }

    private String renderAdMarkup(String markupType, String adm, SmaatoBidExt bidExt) {
        return switch (markupType) {
            case SMT_AD_TYPE_IMG, SMT_ADTYPE_RICHMEDIA -> extractAdmBanner(adm, bidExt.getCurls());
            case SMT_ADTYPE_VIDEO -> adm;
            case SMT_ADTYPE_NATIVE -> extractNative(adm);
            default -> throw new PreBidException("Unknown markup type " + markupType);
        };
    }

    private String extractAdmBanner(String adm, List<String> curls) {
        if (CollectionUtils.isEmpty(curls)) {
            return adm;
        }

        final StringBuilder clickEvent = new StringBuilder();
        curls.forEach(url -> clickEvent.append(
                "fetch(decodeURIComponent('%s'.replace(/\\+/g, ' ')), {cache: 'no-cache'});"
                                .formatted(HttpUtil.encodeUrl(StringUtils.stripToEmpty(url)))));

        return "<div style=\"cursor:pointer\" onclick=\"%s\">%s</div>".formatted(clickEvent, adm);
    }

    private String extractNative(String adm) {
        try {
            final SmaatoNativeAd nativeAd = mapper.decodeValue(adm, SmaatoNativeAd.class);
            return mapper.encodeToString(nativeAd.getNativeRequest());
        } catch (DecodeException | EncodeException e) {
            throw new PreBidException("Cannot decode bid.adm: " + e.getMessage(), e);
        }
    }

    private static BidType getBidType(String markupType) {
        return switch (markupType) {
            case SMT_AD_TYPE_IMG, SMT_ADTYPE_RICHMEDIA -> BidType.banner;
            case SMT_ADTYPE_VIDEO -> BidType.video;
            case SMT_ADTYPE_NATIVE -> BidType.xNative;
            default -> throw new PreBidException("Invalid markupType " + markupType);
        };
    }

    private static <T, R> R getIfNotNullOrThrow(T target, Function<T, R> getter, String propertyName) {
        final R result = getIfNotNull(target, getter);
        if (result == null) {
            throw new PreBidException("Missing %s property.".formatted(propertyName));
        }
        return result;
    }

    private static <T, R> R getIfNotNull(T target, Function<T, R> getter) {
        return target != null ? getter.apply(target) : null;
    }
}
