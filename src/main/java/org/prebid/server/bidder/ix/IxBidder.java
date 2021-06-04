package org.prebid.server.bidder.ix;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ix.ExtImpIx;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidVideo;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * ix {@link Bidder} implementation.
 */
public class IxBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpIx>> IX_EXT_TYPE_REFERENCE = new
            TypeReference<ExtPrebid<?, ExtImpIx>>() {
            };

    // maximum number of bid requests
    private static final int REQUEST_LIMIT = 20;

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public IxBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {

        final List<BidderError> errors = new ArrayList<>();

        // First Banner.Format in every Imp have priority
        final List<BidRequest> prioritizedRequests = new ArrayList<>();
        final List<BidRequest> multiSizeRequests = new ArrayList<>();

        for (Imp imp : bidRequest.getImp()) {
            try {
                final ExtImpIx extImpIx = parseImpExt(imp);
                final List<BidRequest> bidRequests = makeBidRequests(bidRequest, imp, extImpIx);

                prioritizedRequests.add(bidRequests.get(0));
                multiSizeRequests.addAll(bidRequests.subList(1, bidRequests.size()));

                if (prioritizedRequests.size() == REQUEST_LIMIT) {
                    break;
                }
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        final List<BidRequest> modifiedRequests = Stream.concat(
                prioritizedRequests.stream(), multiSizeRequests.stream())
                .limit(REQUEST_LIMIT)
                .collect(Collectors.toList());

        if (modifiedRequests.isEmpty()) {
            errors.add(BidderError.badInput("No valid impressions in the bid request"));
            return Result.withErrors(errors);
        }

        final List<HttpRequest<BidRequest>> httpRequests = modifiedRequests.stream()
                .map(request -> HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(endpointUrl)
                        .body(mapper.encode(request))
                        .headers(HttpUtil.headers())
                        .payload(request)
                        .build())
                .collect(Collectors.toList());

        return Result.of(httpRequests, errors);
    }

    private ExtImpIx parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), IX_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private static List<BidRequest> makeBidRequests(BidRequest bidRequest,
                                                    Imp imp,
                                                    ExtImpIx extImpIx) {
        final ArrayList<BidRequest> modifiedBidRequests = new ArrayList<>();
        final Site modifiedSite = modifySite(bidRequest.getSite(), extImpIx);

        final List<Imp> modifiedImps = modifyImps(imp);
        for (Imp modifiedImp : modifiedImps) {
            final BidRequest modifiedBidRequest = bidRequest.toBuilder()
                    .site(modifiedSite)
                    .imp(Collections.singletonList(modifiedImp))
                    .build();
            modifiedBidRequests.add(modifiedBidRequest);
        }

        return modifiedBidRequests;
    }

    private static Site modifySite(Site site, ExtImpIx extImpIx) {
        return site == null
                ? null
                : site.toBuilder()
                .publisher(modifyPublisher(site.getPublisher(), extImpIx.getSiteId()))
                .build();
    }

    private static Publisher modifyPublisher(Publisher publisher, String siteId) {
        return publisher == null
                ? Publisher.builder().id(siteId).build()
                : publisher.toBuilder().id(siteId).build();
    }

    private static List<Imp> modifyImps(Imp imp) {
        final Banner impBanner = imp.getBanner();
        if (impBanner == null) {
            return Collections.singletonList(imp);
        }

        return modifyBanners(impBanner).stream()
                .map(banner -> imp.toBuilder().banner(banner).build())
                .collect(Collectors.toList());
    }

    private static List<Banner> modifyBanners(Banner banner) {
        final ArrayList<Banner> modifiedBanners = new ArrayList<>();
        final List<Format> formats = getFormats(banner);
        for (Format format : formats) {
            final Banner modifiedBanner = banner.toBuilder()
                    .format(Collections.singletonList(format))
                    .w(format.getW())
                    .h(format.getH())
                    .build();
            modifiedBanners.add(modifiedBanner);
        }

        return modifiedBanners;
    }

    // Cant be empty because of request validation
    private static List<Format> getFormats(Banner banner) {
        final Integer bannerW = banner.getW();
        final Integer bannerH = banner.getH();
        final List<Format> bannerFormats = banner.getFormat();
        if (CollectionUtils.isEmpty(bannerFormats) && bannerW != null && bannerH != null) {
            final Format format = Format.builder()
                    .w(bannerW)
                    .h(bannerH)
                    .build();
            return Collections.singletonList(format);
        }
        return bannerFormats;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            final BidRequest payload = httpCall.getRequest().getPayload();
            return Result.withValues(extractBids(bidResponse, payload));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse, BidRequest bidRequest) {
        return bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())
                ? Collections.emptyList()
                : bidsFromResponse(bidResponse, bidRequest);
    }

    private List<BidderBid> bidsFromResponse(BidResponse bidResponse, BidRequest bidRequest) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> toBidderBid(bid, bidRequest, bidResponse))
                .collect(Collectors.toList());
    }

    private BidderBid toBidderBid(Bid bid, BidRequest bidRequest, BidResponse bidResponse) {
        final BidType bidType = getBidType(bid.getImpid(), bidRequest.getImp());
        final ObjectNode bidExt = bid.getExt();
        final ExtBidPrebid extPrebid = bidExt != null ? parseBidExt(bidExt) : null;
        final ExtBidPrebidVideo extVideo = extPrebid != null ? extPrebid.getVideo() : null;
        final boolean bidHasNoSizes = bid.getH() == null || bid.getW() == null;
        final Banner banner = bidRequest.getImp().get(0).getBanner();

        if ((bidHasNoSizes && banner != null) || (extVideo != null)) {
            final Bid.BidBuilder bidBuilder = bid.toBuilder();

            if (bidType == BidType.banner && bidHasNoSizes && banner != null) {
                bidBuilder
                        .w(banner.getW())
                        .h(banner.getH())
                        .build();
            }

            if (bidType == BidType.video && extVideo != null) {
                bidBuilder.ext(resolveBidExt(extVideo.getDuration()));
                if (CollectionUtils.isEmpty(bid.getCat())) {
                    bidBuilder.cat(Collections.singletonList(extVideo.getPrimaryCategory())).build();
                }
            }
            bid = bidBuilder.build();
        }

        return BidderBid.of(bid, bidType, bidResponse.getCur());
    }

    private static BidType getBidType(String impId, List<Imp> imps) {
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
        throw new PreBidException(String.format("Unmatched impression id %s", impId));
    }

    private ExtBidPrebid parseBidExt(ObjectNode bidExt) {
        try {
            return mapper.mapper().treeToValue(bidExt, ExtBidPrebid.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private ObjectNode resolveBidExt(Integer duration) {
        return mapper.mapper().valueToTree(ExtBidPrebid.builder()
                .video(ExtBidPrebidVideo.of(duration, null))
                .build());
    }
}
