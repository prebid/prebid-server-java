package org.prebid.server.bidder.smaato;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.Endpoint;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.smaato.proto.SmaatoBidExt;
import org.prebid.server.bidder.smaato.proto.SmaatoBidRequestExt;
import org.prebid.server.bidder.smaato.proto.SmaatoImage;
import org.prebid.server.bidder.smaato.proto.SmaatoImageAd;
import org.prebid.server.bidder.smaato.proto.SmaatoImg;
import org.prebid.server.bidder.smaato.proto.SmaatoMediaData;
import org.prebid.server.bidder.smaato.proto.SmaatoRichMediaAd;
import org.prebid.server.bidder.smaato.proto.SmaatoRichmedia;
import org.prebid.server.bidder.smaato.proto.SmaatoSiteExtData;
import org.prebid.server.bidder.smaato.proto.SmaatoUserExtData;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidPbs;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.smaato.ExtImpSmaato;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidVideo;
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
            new TypeReference<ExtPrebid<?, ExtImpSmaato>>() {
            };
    private static final String CLIENT_VERSION = "prebid_server_0.4";
    private static final String SMT_ADTYPE_HEADER = "X-Smt-Adtype";
    private static final String SMT_EXPIRES_HEADER = "X-Smt-Expires";
    private static final String SMT_AD_TYPE_IMG = "Img";
    private static final String SMT_ADTYPE_RICHMEDIA = "Richmedia";
    private static final String SMT_ADTYPE_VIDEO = "Video";

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
        if (yob != null && yob != 0) {
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
        final ExtSite siteExt = getIfNotNull(site, Site::getExt);
        if (siteExt != null) {
            final SmaatoSiteExtData data = convertExt(siteExt.getData(), SmaatoSiteExtData.class);
            final String keywords = getIfNotNull(data, SmaatoSiteExtData::getKeywords);
            return Site.builder().keywords(keywords).ext(null).build();
        }
        return site;
    }

    private <T> T convertExt(ObjectNode ext, Class<T> className) {
        try {
            return mapper.mapper().convertValue(ext, className);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(String.format("Cannot decode extension: %s", e.getMessage()), e);
        }
    }

    private static boolean isVideoRequest(BidRequest bidRequest) {
        final ExtRequestPrebid prebid = getIfNotNull(bidRequest.getExt(), ExtRequest::getPrebid);
        final ExtRequestPrebidPbs pbs = getIfNotNull(prebid, ExtRequestPrebid::getPbs);
        final String endpointName = getIfNotNull(pbs, ExtRequestPrebidPbs::getEndpoint);

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
                .collect(Collectors.toList());
    }

    private static String extractPod(Imp imp) {
        return imp.getId().split("_")[0];
    }

    private BidRequest preparePodRequest(BidRequest bidRequest, List<Imp> imps, List<BidderError> errors) {
        try {
            final ExtImpSmaato extImpSmaato = mapper.mapper().convertValue(imps.get(0).getExt(),
                    SMAATO_EXT_TYPE_REFERENCE).getBidder();

            final String publisherId = getIfNotNullOrThrow(extImpSmaato, ExtImpSmaato::getPublisherId, "publisherId");
            final String adBreakId = getIfNotNullOrThrow(extImpSmaato, ExtImpSmaato::getAdbreakId, "adbreakId");

            return modifyBidRequest(bidRequest, publisherId, () -> modifyImpsForAdBreak(imps, adBreakId));
        } catch (PreBidException | IllegalArgumentException e) {
            errors.add(BidderError.badInput(e.getMessage()));
            return null;
        }
    }

    private BidRequest modifyBidRequest(BidRequest bidRequest, String publisherId, Supplier<List<Imp>> impSupplier) {
        final Publisher publisher = Publisher.builder().id(publisherId).build();
        final Site site = bidRequest.getSite();
        final App app = bidRequest.getApp();

        final BidRequest.BidRequestBuilder bidRequestBuilder = bidRequest.toBuilder();
        if (site != null) {
            bidRequestBuilder.site(site.toBuilder().publisher(publisher).build());
        } else if (app != null) {
            bidRequestBuilder.app(app.toBuilder().publisher(publisher).build());
        } else {
            throw new PreBidException("Missing Site/App.");
        }

        return bidRequestBuilder.imp(impSupplier.get()).build();
    }

    private List<Imp> modifyImpsForAdBreak(List<Imp> imps, String adBreakId) {
        return IntStream.range(0, imps.size())
                .mapToObj(idx -> modifyImpForAdBreak(imps.get(idx), idx + 1, adBreakId))
                .collect(Collectors.toList());
    }

    private Imp modifyImpForAdBreak(Imp imp, Integer sequence, String adBreakId) {
        final Video modifiedVideo = imp.getVideo().toBuilder()
                .sequence(sequence)
                .ext(mapper.mapper().createObjectNode().set("context", TextNode.valueOf("adpod")))
                .build();
        return imp.toBuilder()
                .tagid(adBreakId)
                .video(modifiedVideo)
                .ext(null)
                .build();
    }

    private List<HttpRequest<BidRequest>> constructIndividualRequests(BidRequest bidRequest, List<BidderError> errors) {
        return splitImps(bidRequest.getImp(), errors).stream()
                .map(imp -> prepareIndividualRequest(bidRequest, imp, errors))
                .filter(Objects::nonNull)
                .map(this::constructHttpRequest)
                .collect(Collectors.toList());
    }

    private List<Imp> splitImps(List<Imp> imps, List<BidderError> errors) {
        final List<Imp> splitImps = new ArrayList<>();

        for (Imp imp : imps) {
            final Banner banner = imp.getBanner();
            final Video video = imp.getVideo();
            if (video == null && banner == null) {
                errors.add(BidderError.badInput("Invalid MediaType. Smaato only supports Banner and Video."));
                continue;
            }

            if (video != null) {
                splitImps.add(imp.toBuilder().banner(null).build());
            }
            if (banner != null) {
                splitImps.add(imp.toBuilder().video(null).build());
            }
        }

        return splitImps;
    }

    private BidRequest prepareIndividualRequest(BidRequest bidRequest, Imp imp, List<BidderError> errors) {
        try {
            final ExtImpSmaato extImpSmaato = mapper.mapper().convertValue(imp.getExt(),
                    SMAATO_EXT_TYPE_REFERENCE).getBidder();
            final String publisherId = getIfNotNullOrThrow(extImpSmaato, ExtImpSmaato::getPublisherId, "publisherId");
            final String adSpaceId = getIfNotNullOrThrow(extImpSmaato, ExtImpSmaato::getAdspaceId, "adspaceId");

            return modifyBidRequest(bidRequest, publisherId, () -> modifyImpForAdSpace(imp, adSpaceId));
        } catch (PreBidException | IllegalArgumentException e) {
            errors.add(BidderError.badInput(e.getMessage()));
            return null;
        }
    }

    private List<Imp> modifyImpForAdSpace(Imp imp, String adSpaceId) {
        final Imp modifiedImp = imp.toBuilder()
                .tagid(adSpaceId)
                .banner(getIfNotNull(imp.getBanner(), SmaatoBidder::modifyBanner))
                .ext(null)
                .build();

        return Collections.singletonList(modifiedImp);
    }

    private static Banner modifyBanner(Banner banner) {
        if (banner.getW() != null && banner.getH() != null) {
            return banner;
        }
        final List<Format> format = banner.getFormat();
        if (CollectionUtils.isEmpty(format)) {
            throw new PreBidException("No sizes provided for Banner.");
        }
        final Format firstFormat = format.get(0);
        return banner.toBuilder().w(firstFormat.getW()).h(firstFormat.getH()).build();
    }

    private HttpRequest<BidRequest> constructHttpRequest(BidRequest bidRequest) {
        return HttpRequest.<BidRequest>builder()
                .uri(endpointUrl)
                .method(HttpMethod.POST)
                .headers(HttpUtil.headers())
                .body(mapper.encodeToBytes(bidRequest))
                .payload(bidRequest)
                .build();
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
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

        final List<BidderError> errors = new ArrayList<>();
        final List<BidderBid> bidderBids = bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> bidderBid(bid, bidResponse.getCur(), headers, errors))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return Result.of(bidderBids, errors);
    }

    private BidderBid bidderBid(Bid bid, String currency, MultiMap headers, List<BidderError> errors) {
        try {
            final String bidAdm = bid.getAdm();
            if (StringUtils.isBlank(bidAdm)) {
                throw new PreBidException(String.format("Empty ad markup in bid with id: %s", bid.getId()));
            }
            final String markupType = getAdMarkupType(headers, bidAdm);
            final BidType bidType = getBidType(markupType);
            final Bid updatedBid = bid.toBuilder()
                    .adm(renderAdMarkup(markupType, bidAdm))
                    .exp(getTtl(headers))
                    .ext(buildExtPrebid(bid, bidType))
                    .build();
            return BidderBid.of(updatedBid, bidType, currency);
        } catch (PreBidException e) {
            errors.add(BidderError.badInput(e.getMessage()));
            return null;
        }
    }

    private ObjectNode buildExtPrebid(Bid bid, BidType bidType) {
        final ExtBidPrebidVideo extBidPrebidVideo = getExtBidPrebidVideo(bid, bidType);
        final ExtBidPrebid extBidPrebid = ExtBidPrebid.builder().video(extBidPrebidVideo).build();
        return mapper.mapper().valueToTree(ExtPrebid.of(extBidPrebid, null));
    }

    private ExtBidPrebidVideo getExtBidPrebidVideo(Bid bid, BidType bidType) {
        final ObjectNode bidExt = bid.getExt();
        if (bidType != BidType.video || bidExt == null) {
            return null;
        }

        final List<String> categories = bid.getCat();
        final String primaryCategory = CollectionUtils.isNotEmpty(categories) ? categories.get(0) : null;
        try {
            final SmaatoBidExt smaatoBidExt = mapper.mapper().convertValue(bidExt, SmaatoBidExt.class);
            return ExtBidPrebidVideo.of(smaatoBidExt.getDuration(), primaryCategory);
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

    private static String getAdMarkupType(MultiMap headers, String adm) {
        final String adMarkupType = headers.get(SMT_ADTYPE_HEADER);
        if (StringUtils.isNotBlank(adMarkupType)) {
            return adMarkupType;
        } else if (adm.startsWith("{\"image\":")) {
            return SMT_AD_TYPE_IMG;
        } else if (adm.startsWith("{\"richmedia\":")) {
            return SMT_ADTYPE_RICHMEDIA;
        } else if (adm.startsWith("<?xml")) {
            return SMT_ADTYPE_VIDEO;
        }
        throw new PreBidException(String.format("Invalid ad markup %s.", adm));
    }

    private String renderAdMarkup(String markupType, String adm) {
        switch (markupType) {
            case SMT_AD_TYPE_IMG:
                return extractAdmImage(adm);
            case SMT_ADTYPE_RICHMEDIA:
                return extractAdmRichMedia(adm);
            case SMT_ADTYPE_VIDEO:
                return markupType;
            default:
                throw new PreBidException(String.format("Unknown markup type %s", markupType));
        }
    }

    private String extractAdmImage(String adm) {
        final SmaatoImageAd imageAd = convertAdmToAd(adm, SmaatoImageAd.class);
        final SmaatoImage image = imageAd.getImage();
        if (image == null) {
            throw new PreBidException("bid.adm.image is empty");
        }

        final StringBuilder clickEvent = new StringBuilder();
        CollectionUtils.emptyIfNull(image.getClickTrackers())
                .forEach(tracker -> clickEvent.append(String.format(
                        "fetch(decodeURIComponent('%s'.replace(/\\+/g, ' ')), {cache: 'no-cache'});",
                        HttpUtil.encodeUrl(StringUtils.stripToEmpty(tracker)))));

        final StringBuilder impressionTracker = new StringBuilder();
        CollectionUtils.emptyIfNull(image.getImpressionTrackers())
                .forEach(tracker -> impressionTracker.append(
                        String.format("<img src=\"%s\" alt=\"\" width=\"0\" height=\"0\"/>", tracker)));

        final SmaatoImg img = image.getImg();
        return String.format("<div style=\"cursor:pointer\" onclick=\"%s;window.open(decodeURIComponent"
                        + "('%s'.replace(/\\+/g, ' ')));\"><img src=\"%s\" width=\"%d\" height=\"%d\"/>%s</div>",
                clickEvent,
                HttpUtil.encodeUrl(StringUtils.stripToEmpty(getIfNotNull(img, SmaatoImg::getCtaurl))),
                StringUtils.stripToEmpty(getIfNotNull(img, SmaatoImg::getUrl)),
                stripToZero(getIfNotNull(img, SmaatoImg::getW)),
                stripToZero(getIfNotNull(img, SmaatoImg::getH)),
                impressionTracker);
    }

    private String extractAdmRichMedia(String adm) {
        final SmaatoRichMediaAd richMediaAd = convertAdmToAd(adm, SmaatoRichMediaAd.class);
        final SmaatoRichmedia richmedia = richMediaAd.getRichmedia();
        if (richmedia == null) {
            throw new PreBidException("bid.adm.richmedia is empty");
        }

        final StringBuilder clickEvent = new StringBuilder();
        CollectionUtils.emptyIfNull(richmedia.getClickTrackers())
                .forEach(tracker -> clickEvent.append(
                        String.format("fetch(decodeURIComponent('%s'), {cache: 'no-cache'});",
                                HttpUtil.encodeUrl(StringUtils.stripToEmpty(tracker)))));

        final StringBuilder impressionTracker = new StringBuilder();
        CollectionUtils.emptyIfNull(richmedia.getImpressionTrackers())
                .forEach(tracker -> impressionTracker.append(
                        String.format("<img src=\"%s\" alt=\"\" width=\"0\" height=\"0\"/>", tracker)));

        return String.format("<div onclick=\"%s\">%s%s</div>",
                clickEvent,
                StringUtils.stripToEmpty(getIfNotNull(richmedia.getMediadata(), SmaatoMediaData::getContent)),
                impressionTracker);
    }

    private <T> T convertAdmToAd(String value, Class<T> className) {
        try {
            return mapper.decodeValue(value, className);
        } catch (DecodeException e) {
            throw new PreBidException(String.format("Cannot decode bid.adm: %s", e.getMessage()), e);
        }
    }

    private static BidType getBidType(String markupType) {
        switch (markupType) {
            case SMT_AD_TYPE_IMG:
            case SMT_ADTYPE_RICHMEDIA:
                return BidType.banner;
            case SMT_ADTYPE_VIDEO:
                return BidType.video;
            default:
                throw new PreBidException(String.format("Invalid markupType %s", markupType));
        }
    }

    private static <T, R> R getIfNotNullOrThrow(T target, Function<T, R> getter, String propertyName) {
        final R result = getIfNotNull(target, getter);
        if (result == null) {
            throw new PreBidException(String.format("Missing %s property.", propertyName));
        }
        return result;
    }

    private static <T, R> R getIfNotNull(T target, Function<T, R> getter) {
        return target != null ? getter.apply(target) : null;
    }

    private static int stripToZero(Integer target) {
        return ObjectUtils.defaultIfNull(target, 0);
    }
}
