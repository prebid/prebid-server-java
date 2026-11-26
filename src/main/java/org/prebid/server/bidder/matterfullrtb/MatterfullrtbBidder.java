package org.prebid.server.bidder.matterfullrtb;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.matterfull.ExtImpMatterfullrtb;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.Uri;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MatterfullrtbBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpMatterfullrtb>> TYPE_REFERENCE = new TypeReference<>() {
    };
    private static final String PUBLISHER_ID_MACRO = "PublisherID";

    private final Uri endpointUrl;
    private final JacksonMapper mapper;

    public MatterfullrtbBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = Uri.of(endpointUrl);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();
        final Map<String, List<Imp>> publisherIdToImps = new HashMap<>();

        for (Imp imp : bidRequest.getImp()) {
            try {
                final ExtImpMatterfullrtb extImp = parseImpExt(imp);
                final Imp modifiedImp = modifyImp(imp);
                publisherIdToImps.computeIfAbsent(extImp.getPublisherId(), _ -> new ArrayList<>())
                        .add(modifiedImp);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (publisherIdToImps.isEmpty()) {
            return Result.withErrors(errors);
        }

        for (Map.Entry<String, List<Imp>> publisherIdImps : publisherIdToImps.entrySet()) {
            final BidRequest modifiedBidRequest = modifyRequest(publisherIdImps.getValue(), bidRequest);
            httpRequests.add(BidderUtil.defaultRequest(
                    modifiedBidRequest,
                    getResolvedUrl(publisherIdImps.getKey()),
                    mapper));
        }

        return Result.of(httpRequests, errors);
    }

    private ExtImpMatterfullrtb parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(
                    String.format("invalid imp.ext for impression %s. %s", imp.getId(), e.getMessage()));
        }
    }

    private Imp modifyImp(Imp imp) {
        return imp.toBuilder()
                .ext(null)
                .banner(modifyBannerImp(imp.getBanner()))
                .build();
    }

    private Banner modifyBannerImp(Banner banner) {
        if (banner == null) {
            return null;
        }

        if (banner.getW() == null || banner.getH() == null) {
            final List<Format> formats = banner.getFormat();
            if (formats == null || formats.isEmpty()) {
                throw new PreBidException("Expected at least one banner.format entry or explicit w/h");
            }
            final Format format = banner.getFormat().getFirst();
            return banner.toBuilder()
                    .w(format.getW())
                    .h(format.getH())
                    .format(modifyFormat(banner.getFormat()))
                    .build();
        }
        return banner;
    }

    private List<Format> modifyFormat(List<Format> formats) {
        if (formats.size() > 1) {
            return new ArrayList<>(formats.subList(1, formats.size()));
        }
        return null;
    }

    private BidRequest modifyRequest(List<Imp> imps, BidRequest request) {
        final Site site = request.getSite();
        final App app = request.getApp();

        return request.toBuilder()
                .imp(imps)
                .site(site != null ? modifySite(site) : null)
                .app(app != null ? modifyApp(app) : null)
                .build();
    }

    private static Site modifySite(Site site) {
        return site.toBuilder().publisher(null).domain("").build();
    }

    private static App modifyApp(App app) {
        return app.toBuilder().publisher(null).build();
    }

    private String getResolvedUrl(String publisherId) {
        return endpointUrl.replaceMacro(PUBLISHER_ID_MACRO, publisherId).expand();
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            final List<BidderError> errors = new ArrayList<>();
            final List<BidderBid> bidderBids = extractBids(bidResponse, bidRequest, errors);

            return Result.of(bidderBids, errors);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse,
                                               BidRequest bidRequest,
                                               List<BidderError> errors) {

        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .flatMap(seatBid -> seatBid.getBid().stream())
                .map(bid -> toBidderBid(bid, bidRequest.getImp(), bidResponse.getCur(), errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private static BidderBid toBidderBid(Bid bid, List<Imp> imps, String currency, List<BidderError> errors) {
        try {
            final BidType bidType = getBidMediaType(bid, imps);
            return BidderBid.of(bid, bidType, currency);
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }
    }

    private static BidType getBidMediaType(Bid bid, List<Imp> imps) {
        final Integer mType = bid.getMtype();
        return switch (mType) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            case 3 -> BidType.audio;
            case 4 -> BidType.xNative;
            case 0 -> getMediaTypeForImpId(bid.getImpid(), imps);
            case null -> getMediaTypeForImpId(bid.getImpid(), imps);
            default -> throw new PreBidException(
                    String.format("Unsupported mtype %d for impression with ID: %s", mType, bid.getImpid()));
        };
    }

    private static BidType getMediaTypeForImpId(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                return getMediaTypeForImp(imp);
            }
        }
        throw new PreBidException(String.format("Failed to find impression for ID: %s", impId));
    }

    private static BidType getMediaTypeForImp(Imp imp) {
        int counter = 0;
        BidType bidType = null;
        if (imp.getBanner() != null) {
            counter++;
            bidType = BidType.banner;
        }
        if (imp.getVideo() != null) {
            counter++;
            bidType = BidType.video;
        }
        if (imp.getAudio() != null) {
            counter++;
            bidType = BidType.audio;
        }
        if (imp.getXNative() != null) {
            counter++;
            bidType = BidType.xNative;
        }
        if (counter == 0) {
            throw new PreBidException(
                    String.format("Could not determine media type for impression with ID: %s", imp.getId()));
        } else if (counter != 1) {
            throw new PreBidException(String.format(
                    "Bid must have non-zero mtype for multi-format impression with ID: %s", imp.getId()));
        }
        return bidType;
    }
}
