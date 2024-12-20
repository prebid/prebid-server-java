package org.prebid.server.bidder.ix;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.ntv.EventTrackingMethod;
import com.iab.openrtb.request.ntv.EventType;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.EventTracker;
import com.iab.openrtb.response.Response;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.ix.model.request.IxDiag;
import org.prebid.server.bidder.ix.model.response.IxBidResponse;
import org.prebid.server.bidder.ix.model.response.IxExtBidResponse;
import org.prebid.server.bidder.ix.model.response.NativeV11Wrapper;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.CompositeBidderResponse;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.model.UpdateResult;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidChannel;
import org.prebid.server.proto.openrtb.ext.request.ix.ExtImpIx;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidVideo;
import org.prebid.server.proto.openrtb.ext.response.ExtIgi;
import org.prebid.server.proto.openrtb.ext.response.ExtIgiIgs;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;
import org.prebid.server.version.PrebidVersionProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IxBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpIx>> IX_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final PrebidVersionProvider prebidVersionProvider;
    private final JacksonMapper mapper;

    public IxBidder(String endpointUrl, PrebidVersionProvider prebidVersionProvider, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.prebidVersionProvider = Objects.requireNonNull(prebidVersionProvider);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final Set<String> siteIds = new HashSet<>();
        final List<Imp> imps = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        for (Imp imp : bidRequest.getImp()) {
            try {
                final ExtImpIx impExt = parseImpExt(imp);
                final String siteId = impExt.getSiteId();
                if (StringUtils.isNotEmpty(siteId)) {
                    siteIds.add(siteId);
                }

                imps.add(modifyImp(imp, impExt));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (imps.isEmpty()) {
            return Result.withErrors(errors);
        }

        final BidRequest modifiedBidRequest = modifyBidRequest(bidRequest, imps, siteIds);
        final List<HttpRequest<BidRequest>> httpRequests = Collections.singletonList(
                BidderUtil.defaultRequest(modifiedBidRequest, endpointUrl, mapper));

        return Result.of(httpRequests, errors);
    }

    private ExtImpIx parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), IX_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private Imp modifyImp(Imp imp, ExtImpIx impExt) {
        final UpdateResult<ObjectNode> impExtUpdateResult = modifyImpExt(imp, impExt);
        final UpdateResult<Banner> bannerUpdateResult = modifyImpBanner(imp.getBanner());

        return impExtUpdateResult.isUpdated() || bannerUpdateResult.isUpdated()
                ? imp.toBuilder().ext(impExtUpdateResult.getValue()).banner(bannerUpdateResult.getValue()).build()
                : imp;
    }

    private UpdateResult<ObjectNode> modifyImpExt(Imp imp, ExtImpIx extImpIx) {
        final String sid = extImpIx.getSid();
        final ObjectNode impExt = imp.getExt();
        if (StringUtils.isEmpty(sid)) {
            return UpdateResult.unaltered(impExt);
        }

        final ObjectNode updatedExt = impExt.deepCopy();
        updatedExt.set("sid", TextNode.valueOf(sid));

        return UpdateResult.updated(updatedExt);
    }

    private UpdateResult<Banner> modifyImpBanner(Banner banner) {
        if (banner == null) {
            return UpdateResult.unaltered(null);
        }

        final List<Format> formats = banner.getFormat();
        final Integer w = banner.getW();
        final Integer h = banner.getH();

        if (CollectionUtils.isEmpty(formats) && h != null && w != null) {
            final List<Format> newFormats = Collections.singletonList(Format.builder().w(w).h(h).build());
            final Banner modifiedBanner = banner.toBuilder().format(newFormats).build();
            return UpdateResult.updated(modifiedBanner);
        } else if (formats.size() == 1) {
            final Format format = formats.getFirst();
            final Banner modifiedBanner = banner.toBuilder().w(format.getW()).h(format.getH()).build();
            return UpdateResult.updated(modifiedBanner);
        }

        return UpdateResult.unaltered(banner);
    }

    private BidRequest modifyBidRequest(BidRequest bidRequest, List<Imp> imps, Set<String> siteIds) {
        final String publisherId = siteIds.size() == 1 ? siteIds.stream().findAny().get() : null;

        return bidRequest.toBuilder()
                .imp(imps)
                .site(modifySite(bidRequest.getSite(), publisherId))
                .app(modifyApp(bidRequest.getApp(), publisherId))
                .ext(modifyRequestExt(bidRequest.getExt(), siteIds))
                .build();
    }

    private static Site modifySite(Site site, String id) {
        return Optional.ofNullable(site)
                .map(Site::toBuilder)
                .map(builder -> builder.publisher(modifyPublisher(site.getPublisher(), id)))
                .map(Site.SiteBuilder::build)
                .orElse(null);
    }

    private static App modifyApp(App app, String id) {
        return Optional.ofNullable(app)
                .map(App::toBuilder)
                .map(builder -> builder.publisher(modifyPublisher(app.getPublisher(), id)))
                .map(App.AppBuilder::build)
                .orElse(null);
    }

    private static Publisher modifyPublisher(Publisher publisher, String id) {
        return Optional.ofNullable(publisher)
                .map(Publisher::toBuilder)
                .orElseGet(Publisher::builder)
                .id(id)
                .build();
    }

    private ExtRequest modifyRequestExt(ExtRequest extRequest, Set<String> siteIds) {
        final ExtRequest modifiedExt;

        if (extRequest != null) {
            modifiedExt = ExtRequest.of(extRequest.getPrebid());
            mapper.fillExtension(modifiedExt, extRequest);
        } else {
            modifiedExt = ExtRequest.empty();
        }

        modifiedExt.addProperty("ixdiag", mapper.mapper().valueToTree(makeDiagData(extRequest, siteIds)));
        return modifiedExt;
    }

    private IxDiag makeDiagData(ExtRequest extRequest, Set<String> siteIds) {
        final String pbjsv = Optional.ofNullable(extRequest)
                .map(ExtRequest::getPrebid)
                .map(ExtRequestPrebid::getChannel)
                .map(ExtRequestPrebidChannel::getVersion)
                .orElse(null);

        final String pbsv = prebidVersionProvider.getNameVersionRecord();

        final String multipleSiteIds = siteIds.size() > 1
                ? siteIds.stream().sorted().collect(Collectors.joining(", "))
                : null;

        return IxDiag.of(pbsv, pbjsv, multipleSiteIds);
    }

    @Override
    @Deprecated(since = "Not used, since Bidder.makeBidderResponse(...) was overridden.")
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        return Result.withError(BidderError.generic("Invalid method call"));
    }

    @Override
    public CompositeBidderResponse makeBidderResponse(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final IxBidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), IxBidResponse.class);
            final List<BidderError> errors = new ArrayList<>();

            return CompositeBidderResponse.builder()
                    .bids(extractBids(bidRequest, bidResponse, errors))
                    .igi(extractIgi(bidResponse))
                    .errors(errors)
                    .build();
        } catch (DecodeException e) {
            return CompositeBidderResponse.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidRequest bidRequest,
                                        IxBidResponse bidResponse,
                                        List<BidderError> errors) {

        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> toBidderBid(bid, bidRequest, bidResponse, errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private BidderBid toBidderBid(Bid bid, BidRequest bidRequest, IxBidResponse bidResponse, List<BidderError> errors) {
        final BidType bidType;
        try {
            bidType = getBidType(bid, bidRequest.getImp());
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }

        final ExtBidPrebidVideo extBidPrebidVideo = bidType == BidType.video
                ? parseBidExtPrebidVideo(bid.getExt())
                : null;

        final Bid updatedBid = switch (bidType) {
            case video -> updateBidWithVideoAttributes(bid, extBidPrebidVideo);
            case xNative -> updateBidAdmWithNativeAttributes(bid);
            default -> bid;
        };

        return BidderBid.builder()
                .bid(updatedBid)
                .type(bidType)
                .bidCurrency(bidResponse.getCur())
                .videoInfo(bidType == BidType.video ? videoInfo(extBidPrebidVideo) : null)
                .build();
    }

    private static BidType getBidType(Bid bid, List<Imp> imps) {
        return getBidTypeFromMtype(bid.getMtype())
                .or(() -> getBidTypeFromExtPrebidType(bid.getExt()))
                .orElseGet(() -> getBidTypeFromImp(imps, bid.getImpid()));
    }

    private static Optional<BidType> getBidTypeFromMtype(Integer mType) {
        final BidType bidType = switch (mType) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            case 3 -> BidType.audio;
            case 4 -> BidType.xNative;
            case null, default -> null;
        };

        return Optional.ofNullable(bidType);
    }

    private static Optional<BidType> getBidTypeFromExtPrebidType(ObjectNode bidExt) {
        return Optional.ofNullable(bidExt)
                .map(ext -> ext.get("prebid"))
                .map(prebid -> prebid.get("type"))
                .map(JsonNode::asText)
                .map(BidType::fromString);
    }

    private static BidType getBidTypeFromImp(List<Imp> imps, String impId) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() != null) {
                    return BidType.banner;
                } else if (imp.getVideo() != null) {
                    return BidType.video;
                } else if (imp.getXNative() != null) {
                    return BidType.xNative;
                } else if (imp.getAudio() != null) {
                    return BidType.audio;
                }
            }
        }
        throw new PreBidException("Unmatched impression id " + impId);
    }

    private ExtBidPrebidVideo parseBidExtPrebidVideo(ObjectNode bidExt) {
        if (bidExt == null) {
            return null;
        }

        try {
            return mapper.mapper().treeToValue(bidExt.path("prebid").path("video"), ExtBidPrebidVideo.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private Bid updateBidWithVideoAttributes(Bid bid, ExtBidPrebidVideo extBidPrebidVideo) {
        return CollectionUtils.isEmpty(bid.getCat()) && extBidPrebidVideo != null
                ? bid.toBuilder()
                .cat(Collections.singletonList(extBidPrebidVideo.getPrimaryCategory()))
                .build()
                : bid;
    }

    private Bid updateBidAdmWithNativeAttributes(Bid bid) {
        final String adm = bid.getAdm();
        final NativeV11Wrapper nativeV11 = parseBidAdm(adm, NativeV11Wrapper.class);
        final Response responseV11 = ObjectUtil.getIfNotNull(nativeV11, NativeV11Wrapper::getNativeResponse);
        final boolean isV11 = responseV11 != null;
        final Response response = isV11 ? responseV11 : parseBidAdm(adm, Response.class);
        final List<EventTracker> trackers = ObjectUtil.getIfNotNull(response, Response::getEventtrackers);
        final String updatedAdm = CollectionUtils.isNotEmpty(trackers)
                ? mergeNativeImpTrackers(isV11, response, trackers)
                : null;

        return updatedAdm != null
                ? bid.toBuilder().adm(updatedAdm).build()
                : bid;
    }

    private <T> T parseBidAdm(String adm, Class<T> clazz) {
        try {
            return mapper.decodeValue(adm, clazz);
        } catch (IllegalArgumentException | DecodeException e) {
            return null;
        }
    }

    private String mergeNativeImpTrackers(boolean isV11, Response response, List<EventTracker> trackers) {
        return mapper.encodeToString(isV11
                ? NativeV11Wrapper.of(mergeNativeImpTrackers(response, trackers))
                : mergeNativeImpTrackers(response, trackers));
    }

    private static Response mergeNativeImpTrackers(Response response, List<EventTracker> eventTrackers) {
        return response.toBuilder()
                .imptrackers(Stream.concat(
                                eventTrackers.stream()
                                        .filter(IxBidder::isImpTracker)
                                        .map(EventTracker::getUrl),
                                response.getImptrackers().stream())
                        .distinct()
                        .toList())
                .build();
    }

    private static boolean isImpTracker(EventTracker tracker) {
        return Objects.equals(tracker.getMethod(), EventType.IMPRESSION.getValue())
                || Objects.equals(tracker.getEvent(), EventTrackingMethod.IMAGE.getValue());
    }

    private static ExtBidPrebidVideo videoInfo(ExtBidPrebidVideo extBidPrebidVideo) {
        return extBidPrebidVideo != null
                ? ExtBidPrebidVideo.of(extBidPrebidVideo.getDuration(), null)
                : null;
    }

    private List<ExtIgi> extractIgi(IxBidResponse bidResponse) {
        final List<ExtIgiIgs> igs = Optional.ofNullable(bidResponse)
                .map(IxBidResponse::getExt)
                .map(IxExtBidResponse::getProtectedAudienceAuctionConfigs)
                .orElse(Collections.emptyList())
                .stream()
                .filter(Objects::nonNull)
                .map(config -> ExtIgiIgs.builder().impId(config.getBidId()).config(config.getConfig()).build())
                .toList();

        return Collections.singletonList(ExtIgi.builder().igs(igs).build());
    }
}
