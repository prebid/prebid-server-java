package org.prebid.server.bidder.ninthdecimal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
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
import org.prebid.server.proto.openrtb.ext.request.ninthdecimal.ExtImpNinthdecimal;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class NinthdecimalBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpNinthdecimal>> NINTHDECIMAL_EXT_TYPE_REFERENCE = new
            TypeReference<ExtPrebid<?, ExtImpNinthdecimal>>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public NinthdecimalBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();
        try {
            final Map<ExtImpNinthdecimal, List<Imp>> impToExtImp = getImpToExtImp(request, errors);
            httpRequests.addAll(buildBidderRequests(request, impToExtImp));
        } catch (PreBidException e) {
            return Result.of(Collections.emptyList(), errors);
        }

        return Result.of(httpRequests, errors);
    }

    private Map<ExtImpNinthdecimal, List<Imp>> getImpToExtImp(BidRequest request, List<BidderError> errors) {
        final Map<ExtImpNinthdecimal, List<Imp>> extToListOfUpdatedImp = new HashMap<>();
        for (Imp imp : request.getImp()) {
            try {
                final ExtImpNinthdecimal extImpNinthdecimal = parseAndValidateImpExt(imp);
                final Imp updatedImp = updateImp(imp);

                extToListOfUpdatedImp.putIfAbsent(extImpNinthdecimal, new ArrayList<>());
                extToListOfUpdatedImp.get(extImpNinthdecimal).add(updatedImp);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (extToListOfUpdatedImp.isEmpty()) {
            throw new PreBidException("No appropriate impressions");
        }

        return extToListOfUpdatedImp;
    }

    private ExtImpNinthdecimal parseAndValidateImpExt(Imp imp) {
        final ExtImpNinthdecimal extImpNinthdecimal;
        try {
            extImpNinthdecimal = mapper.mapper().convertValue(imp.getExt(), NINTHDECIMAL_EXT_TYPE_REFERENCE)
                    .getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }

        if (StringUtils.isBlank(extImpNinthdecimal.getPubid())) {
            throw new PreBidException("No pubid value provided");
        }

        return extImpNinthdecimal;
    }

    private static Imp updateImp(Imp imp) {
        final Imp.ImpBuilder impBuilder = imp.toBuilder().ext(null);

        final Video video = imp.getVideo();
        if (video != null) {
            return impBuilder.banner(null)
                    .audio(null)
                    .xNative(null)
                    .build();
        }

        final Banner banner = imp.getBanner();
        if (banner != null) {
            return impBuilder.banner(modifyImpBanner(banner)).build();
        }

        throw new PreBidException("Unsupported impression has been received");
    }

    private static Banner modifyImpBanner(Banner banner) {
        if (banner != null && (banner.getW() == null || banner.getH() == null)) {
            final Banner.BannerBuilder bannerBuilder = banner.toBuilder();
            final List<Format> originalFormat = banner.getFormat();

            if (CollectionUtils.isEmpty(originalFormat)) {
                throw new PreBidException("Expected at least one banner.format entry or explicit w/h");
            }

            final List<Format> formatSkipFirst = originalFormat.subList(1, originalFormat.size());
            bannerBuilder.format(formatSkipFirst);

            Format firstFormat = originalFormat.get(0);
            bannerBuilder.w(firstFormat.getW());
            bannerBuilder.h(firstFormat.getH());

            return bannerBuilder.build();
        }

        return banner;
    }

    private List<HttpRequest<BidRequest>> buildBidderRequests(BidRequest bidRequest,
                                                              Map<ExtImpNinthdecimal, List<Imp>> impExtToListOfImps) {
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();

        for (Map.Entry<ExtImpNinthdecimal, List<Imp>> impExtAndListOfImp : impExtToListOfImps.entrySet()) {
            final ExtImpNinthdecimal extImpNinthdecimal = impExtAndListOfImp.getKey();
            final List<Imp> imps = impExtAndListOfImp.getValue();
            final BidRequest updatedBidRequest = makeBidRequest(bidRequest, extImpNinthdecimal, imps);

            final MultiMap headers = HttpUtil.headers()
                    .add(HttpUtil.X_OPENRTB_VERSION_HEADER, "2.5");
            final String createdEndpoint = endpointUrl + extImpNinthdecimal.getPubid();

            final HttpRequest<BidRequest> createdBidRequest = HttpRequest.<BidRequest>builder()
                    .method(HttpMethod.POST)
                    .uri(createdEndpoint)
                    .body(mapper.encodeToBytes(updatedBidRequest))
                    .headers(headers)
                    .payload(bidRequest)
                    .build();

            httpRequests.add(createdBidRequest);
        }

        return httpRequests;
    }

    private static BidRequest makeBidRequest(BidRequest preBidRequest, ExtImpNinthdecimal extImpNinthdecimal,
                                             List<Imp> imps) {
        final BidRequest.BidRequestBuilder bidRequestBuilder = preBidRequest.toBuilder();
        final Site site = preBidRequest.getSite();
        if (site != null) {
            bidRequestBuilder.site(site.toBuilder().publisher(null).domain("").build());
        }

        final App app = preBidRequest.getApp();
        if (app != null) {
            bidRequestBuilder.app(app.toBuilder().publisher(null).build());
        }
        return bidRequestBuilder.imp(updateImps(imps, extImpNinthdecimal.getPlacement())).build();
    }

    private static List<Imp> updateImps(List<Imp> imps, String placement) {
        return imps.stream()
                .map(imp -> imp.toBuilder().tagid(placement).build())
                .collect(Collectors.toList());
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(httpCall.getRequest().getPayload(), bidResponse), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || bidResponse.getSeatbid() == null) {
            return Collections.emptyList();
        }
        if (bidResponse.getSeatbid().size() != 1) {
            throw new PreBidException(String.format("Invalid SeatBids count: %d", bidResponse.getSeatbid().size()));
        }
        return bidsFromResponse(bidRequest, bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .map(SeatBid::getBid)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, getBidType(bid.getImpid(), bidRequest.getImp()), bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    private static BidType getBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId) && imp.getVideo() != null) {
                return BidType.video;
            }
        }
        return BidType.banner;
    }
}
